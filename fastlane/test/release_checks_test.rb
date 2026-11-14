# Tests for fastlane/release_checks.rb. Self-contained (no test gem needed):
#   ruby fastlane/test/release_checks_test.rb
require "fileutils"
require "tmpdir"
require "open3"
require_relative "../release_checks"

$failures = []
$count = 0

def check(name)
  $count += 1
  yield
rescue => e
  $failures << "#{name}: #{e.message}"
end

def assert_equal(expected, actual)
  raise "expected #{expected.inspect}, got #{actual.inspect}" unless expected == actual
end

def assert(condition, message)
  raise message unless condition
end

# ── parse_properties ──────────────────────────────────────────────────────────

check("parses the key=value forms local.properties uses") do
  props = ReleaseChecks.parse_properties(<<~PROPS)
    # comment
    ! also a comment

    storeFile=/keys/c2k.jks
    storePassword = p@ss w0rd
    keyAlias: c2k
    keyPassword=a=b:c\r
  PROPS
  assert_equal("/keys/c2k.jks", props["storeFile"])
  assert_equal("p@ss w0rd", props["storePassword"])
  assert_equal("c2k", props["keyAlias"])
  assert_equal("a=b:c", props["keyPassword"])  # only the first separator splits; CR dropped
  assert_equal(4, props.size)
end

check("a key with no value maps to an empty string") do
  assert_equal({ "keyAlias" => "" }, ReleaseChecks.parse_properties("keyAlias=\n"))
end

# ── version_info ──────────────────────────────────────────────────────────────

check("reads versionName and versionCode from build.gradle.kts") do
  kts = %(defaultConfig {\n  versionCode = 31\n  versionName = "1.2.24"\n})
  assert_equal(["1.2.24", "31"], ReleaseChecks.version_info(kts))
end

check("the real build.gradle.kts has both") do
  name, code = ReleaseChecks.version_info(File.read(File.expand_path("../../app/build.gradle.kts", __dir__)))
  assert(name && code, "versionName/versionCode not found")
end

# ── changelog_errors ──────────────────────────────────────────────────────────

def with_metadata(locales)
  Dir.mktmpdir do |dir|
    locales.each do |locale, changelogs|
      FileUtils.mkdir_p(File.join(dir, locale, "changelogs"))
      File.write(File.join(dir, locale, "title.txt"), "C2K")
      changelogs.each { |code, text| File.write(File.join(dir, locale, "changelogs", "#{code}.txt"), text) }
    end
    FileUtils.mkdir_p(File.join(dir, "screenshots-not-a-locale"))
    yield dir
  end
end

check("all locales with a changelog pass") do
  with_metadata("en-US" => { 31 => "Fixes." }, "de-DE" => { 31 => "Fehlerbehebungen." }) do |dir|
    assert_equal([], ReleaseChecks.changelog_errors(dir, 31))
  end
end

check("a locale missing the changelog is reported by name") do
  with_metadata("en-US" => { 31 => "Fixes." }, "fr-FR" => { 30 => "Ancien." }) do |dir|
    assert_equal(["fr-FR: missing changelogs/31.txt"], ReleaseChecks.changelog_errors(dir, 31))
  end
end

check("an empty changelog is reported") do
  with_metadata("en-US" => { 31 => " \n" }) do |dir|
    assert_equal(["en-US: changelogs/31.txt is empty"], ReleaseChecks.changelog_errors(dir, 31))
  end
end

check("a changelog over Play's 500 characters is reported; exactly 500 passes") do
  with_metadata("en-US" => { 31 => "é" * 500 }, "ru-RU" => { 31 => "ж" * 501 }) do |dir|
    errors = ReleaseChecks.changelog_errors(dir, 31)
    assert_equal(1, errors.size)
    assert(errors.first.start_with?("ru-RU: changelogs/31.txt is 501 characters"), errors.first)
  end
end

check("no locales at all is an error, not a pass") do
  Dir.mktmpdir { |dir| assert_equal(1, ReleaseChecks.changelog_errors(dir, 31).size) }
end

check("the real metadata passes for the current versionCode") do
  root = File.expand_path("../..", __dir__)
  _, code = ReleaseChecks.version_info(File.read(File.join(root, "app/build.gradle.kts")))
  errors = ReleaseChecks.changelog_errors(File.join(root, "fastlane/metadata/android"), code)
  assert(errors.empty?, errors.join("; "))
end

# ── git_errors ────────────────────────────────────────────────────────────────

# A fake git: maps an argument list to [stdout, success]. Defaults describe a releasable state.
def fake_git(overrides = {})
  responses = {
    %w[fetch --quiet origin main] => ["", true],
    %w[rev-parse --abbrev-ref HEAD] => ["main\n", true],
    %w[rev-parse HEAD] => ["abc1234def\n", true],
    %w[rev-parse origin/main] => ["abc1234def\n", true],
    %w[rev-parse --verify --quiet refs/tags/v1.2.24] => ["", false],
    %w[ls-remote --tags origin refs/tags/v1.2.24] => ["", true]
  }.merge(overrides)
  ->(args) { responses.fetch(args) { raise "unexpected git #{args.join(' ')}" } }
end

check("a pushed main with a new tag passes") do
  assert_equal([], ReleaseChecks.git_errors("v1.2.24", git: fake_git))
end

check("an unpushed HEAD is refused") do
  errors = ReleaseChecks.git_errors("v1.2.24", git: fake_git(%w[rev-parse HEAD] => ["fff9999aaa\n", true]))
  assert_equal(1, errors.size)
  assert(errors.first.include?("is not origin/main"), errors.first)
end

check("another branch is refused") do
  errors = ReleaseChecks.git_errors("v1.2.24", git: fake_git(%w[rev-parse --abbrev-ref HEAD] => ["feature\n", true]))
  assert_equal(["Not on main (on feature)"], errors)
end

check("an existing local tag is refused") do
  errors = ReleaseChecks.git_errors(
    "v1.2.24", git: fake_git(%w[rev-parse --verify --quiet refs/tags/v1.2.24] => ["abc\n", true])
  )
  assert_equal(["Tag v1.2.24 already exists locally"], errors)
end

check("an existing remote tag is refused") do
  errors = ReleaseChecks.git_errors(
    "v1.2.24",
    git: fake_git(%w[ls-remote --tags origin refs/tags/v1.2.24] => ["abc\trefs/tags/v1.2.24\n", true])
  )
  assert_equal(["Tag v1.2.24 already exists on origin"], errors)
end

check("a failed fetch stops the checks") do
  errors = ReleaseChecks.git_errors("v1.2.24", git: fake_git(%w[fetch --quiet origin main] => ["", false]))
  assert_equal(1, errors.size)
  assert(errors.first.start_with?("Could not fetch origin"), errors.first)
end

# ── with_local_tag ────────────────────────────────────────────────────────────

# A fake git that records every call and succeeds.
def recording_git
  calls = []
  [->(args) { calls << args; ["", true] }, calls]
end

check("a pushed release keeps its annotated tag") do
  git, calls = recording_git
  assert_equal(true, ReleaseChecks.with_local_tag("v1.2.24", git: git) { true })
  assert_equal([["tag", "-a", "v1.2.24", "-m", "v1.2.24 (fastlane)"]], calls)
end

check("a failure before the push removes the tag and re-raises") do
  git, calls = recording_git
  raised = begin
    ReleaseChecks.with_local_tag("v1.2.24", git: git) { raise "docker build failed" }
    nil
  rescue => e
    e.message
  end
  assert_equal("docker build failed", raised)
  assert_equal(["tag", "-d", "v1.2.24"], calls.last)
end

check("declining at the prompt (a block that doesn't report a push) removes the tag") do
  git, calls = recording_git
  assert_equal(false, ReleaseChecks.with_local_tag("v1.2.24", git: git) { nil })
  assert_equal(["tag", "-d", "v1.2.24"], calls.last)
end

check("a tag that can't be created stops before the block runs") do
  ran = false
  failing = ->(_args) { ["", false] }
  raised = begin
    ReleaseChecks.with_local_tag("v1.2.24", git: failing) { ran = true }
    false
  rescue
    true
  end
  assert(raised && !ran, "expected a raise without running the release")
end

# ── apksigner_invocation ──────────────────────────────────────────────────────

check("passwords go through the environment, never the arguments") do
  env, argv = ReleaseChecks.apksigner_invocation(
    apksigner: "/sdk/apksigner", keystore: "/keys/c2k.jks", key_alias: "c2k",
    store_pass: "s3cret store", key_pass: "k3y;rm -rf ~", out: "signed.apk", input: "unsigned.apk"
  )
  assert_equal({ "C2K_STORE_PASS" => "s3cret store", "C2K_KEY_PASS" => "k3y;rm -rf ~" }, env)
  assert(argv.none? { |a| a.include?("s3cret") || a.include?("k3y") }, "password leaked into argv")
  assert_equal(
    ["/sdk/apksigner", "sign", "--ks", "/keys/c2k.jks", "--ks-key-alias", "c2k",
     "--ks-pass", "env:C2K_STORE_PASS", "--key-pass", "env:C2K_KEY_PASS",
     "--out", "signed.apk", "unsigned.apk"],
    argv
  )
end

check("apksigner accepts the env: password form") do
  # Real apksigner, if one is installed: a bogus keystore must fail on the keystore, not on
  # the env:NAME password syntax.
  sdk = ENV["ANDROID_SDK_ROOT"] || ENV["ANDROID_HOME"] || File.join(Dir.home, "Android/Sdk")
  apksigner = Dir[File.join(sdk, "build-tools", "*", "apksigner")]
    .max_by { |path| Gem::Version.new(File.basename(File.dirname(path))) rescue Gem::Version.new("0") }
  next unless apksigner
  Dir.mktmpdir do |dir|
    ks = File.join(dir, "missing.jks")
    env, argv = ReleaseChecks.apksigner_invocation(
      apksigner: apksigner, keystore: ks, key_alias: "c2k",
      store_pass: "x", key_pass: "x", out: File.join(dir, "o.apk"), input: File.join(dir, "i.apk")
    )
    out, _status = Open3.capture2e(env, *argv)
    # Unset or unreadable variables fail with "Failed to read Keystore password"; reaching
    # the keystore itself proves the passwords were read from the environment.
    assert(!out.match?(/Failed to read .*password/i), "apksigner couldn't read env: passwords: #{out[0, 300]}")
    assert(out.include?("missing.jks"), "expected a keystore error, got: #{out[0, 300]}")
  end
end

# ──────────────────────────────────────────────────────────────────────────────

if $failures.empty?
  puts "#{$count} checks passed"
else
  puts $failures.map { |f| "FAIL #{f}" }
  puts "#{$failures.size} of #{$count} checks failed"
  exit 1
end
