# Pre-release checks for the `release` lane. Plain Ruby with no fastlane dependency, so
# fastlane/test/release_checks_test.rb can exercise every check without cutting a release.
module ReleaseChecks
  # Google Play rejects release notes longer than this, per locale.
  PLAY_CHANGELOG_LIMIT = 500

  module_function

  # Parses a Java .properties file: "key=value", "key = value" and "key: value", with comments,
  # blank lines and Windows line endings. Values keep inner spaces; leading/trailing ones go.
  def parse_properties(text)
    text.each_line.with_object({}) do |raw, props|
      line = raw.chomp.sub(/\r\z/, "").lstrip
      next if line.empty? || line.start_with?("#", "!")
      key, value = line.split(/\s*[=:]\s*|\s+/, 2)
      props[key] = (value || "").strip
    end
  end

  # versionName and versionCode from app/build.gradle.kts, or nil for either it can't find.
  def version_info(gradle_kts)
    [gradle_kts[/versionName\s*=\s*"([^"]+)"/, 1], gradle_kts[/versionCode\s*=\s*(\d+)/, 1]]
  end

  # Every listing locale (a directory with a title.txt) needs a non-empty changelog for this
  # versionCode within Play's limit. Returns a list of problems; empty means fine.
  def changelog_errors(metadata_dir, version_code)
    locales = Dir.children(metadata_dir).sort.select do |name|
      File.file?(File.join(metadata_dir, name, "title.txt"))
    end
    return ["No listing locales found under #{metadata_dir}"] if locales.empty?

    locales.filter_map do |locale|
      path = File.join(metadata_dir, locale, "changelogs", "#{version_code}.txt")
      if !File.file?(path)
        "#{locale}: missing changelogs/#{version_code}.txt"
      else
        text = File.read(path, encoding: "UTF-8").strip
        if text.empty?
          "#{locale}: changelogs/#{version_code}.txt is empty"
        elsif text.length > PLAY_CHANGELOG_LIMIT
          "#{locale}: changelogs/#{version_code}.txt is #{text.length} characters " \
            "(Play allows #{PLAY_CHANGELOG_LIMIT})"
        end
      end
    end
  end

  # Release from the pushed tip of main, with a tag that doesn't exist yet. `git` runs a git
  # command (an argument list) and returns [stdout, success]; injected so tests can fake it.
  # Fetches origin's main first, so the comparison is against what's actually on GitHub. Tags
  # aren't fetched (a stale local tag would make that fail); the remote tag check asks origin.
  def git_errors(tag, git:)
    errors = []
    _, fetched = git.call(%w[fetch --quiet origin main])
    return ["Could not fetch origin; check the network and try again"] unless fetched

    branch, = git.call(%w[rev-parse --abbrev-ref HEAD])
    errors << "Not on main (on #{branch.strip})" unless branch.strip == "main"

    head, = git.call(%w[rev-parse HEAD])
    remote, = git.call(%w[rev-parse origin/main])
    unless head.strip == remote.strip
      errors << "HEAD (#{head.strip[0, 7]}) is not origin/main (#{remote.strip[0, 7]}); " \
                "push main first so the tag points at a published commit"
    end

    _, local_tag = git.call(["rev-parse", "--verify", "--quiet", "refs/tags/#{tag}"])
    errors << "Tag #{tag} already exists locally" if local_tag

    remote_tags, = git.call(["ls-remote", "--tags", "origin", "refs/tags/#{tag}"])
    errors << "Tag #{tag} already exists on origin" unless remote_tags.strip.empty?

    errors
  end

  # Creates the local release tag and yields. The block returns true once the tag has been
  # pushed. If it returns anything else, or raises (a failed build or signing, or answering no
  # at the prompt), the local tag is deleted again, so a later run can create it.
  def with_local_tag(tag, git:)
    # Annotated, with the message fastlane's add_git_tag gave every earlier release tag.
    _, created = git.call(["tag", "-a", tag, "-m", "#{tag} (fastlane)"])
    raise "Could not create tag #{tag}" unless created
    pushed = false
    begin
      pushed = yield == true
    ensure
      git.call(["tag", "-d", tag]) unless pushed
    end
    pushed
  end

  # The apksigner invocation as [environment, argument list], for system() without a shell.
  # The passwords travel in the environment (apksigner's env:NAME form), never as arguments,
  # which other local processes can read from /proc while it runs.
  def apksigner_invocation(apksigner:, keystore:, key_alias:, store_pass:, key_pass:, out:, input:)
    [
      { "C2K_STORE_PASS" => store_pass, "C2K_KEY_PASS" => key_pass },
      [apksigner, "sign", "--ks", keystore, "--ks-key-alias", key_alias,
       "--ks-pass", "env:C2K_STORE_PASS", "--key-pass", "env:C2K_KEY_PASS",
       "--out", out, input]
    ]
  end
end
