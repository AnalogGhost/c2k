package com.hackerapps.c2k.ui.screen.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackerapps.c2k.R

private data class GuideEntry(val question: String, val answer: String)
private data class GuideSection(val title: String, val entries: List<GuideEntry>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val sections = rememberGuideSections()
    // key: "section-entry", value: expanded
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            itemsIndexed(sections) { sIdx, section ->
                GuideSectionCard(
                    section = section,
                    getExpanded = { eIdx -> expanded["$sIdx-$eIdx"] ?: false },
                    onToggle    = { eIdx ->
                        val key = "$sIdx-$eIdx"
                        expanded[key] = !(expanded[key] ?: false)
                    }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GuideSectionCard(
    section: GuideSection,
    getExpanded: (Int) -> Boolean,
    onToggle: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            section.entries.forEachIndexed { eIdx, entry ->
                if (eIdx > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                GuideEntryRow(
                    entry    = entry,
                    expanded = getExpanded(eIdx),
                    onToggle = { onToggle(eIdx) }
                )
            }
        }
    }
}

@Composable
private fun GuideEntryRow(entry: GuideEntry, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = entry.answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun rememberGuideSections(): List<GuideSection> = remember {
    listOf(
        GuideSection(
            title = "Before You Start",
            entries = listOf(
                GuideEntry(
                    question = "What is conversational pace?",
                    answer = "Conversational pace means running slowly enough that you could hold a full conversation — complete sentences, not gasps between words. If you're too breathless to speak, slow down.\n\nMost beginners run too fast and exhaust themselves in the first minute. Slowing down is not a sign of weakness; it's the correct technique. You should feel like you're working, but comfortably so."
                ),
                GuideEntry(
                    question = "What gear do I actually need?",
                    answer = "A decent pair of running shoes is the one thing worth spending money on. They cushion the repeated impact on your joints and reduce injury risk significantly. If possible, visit a specialist running shop and get fitted — they'll watch you walk or run briefly and recommend the right type.\n\nEverything else — special clothes, heart rate monitors, GPS watches — is optional. Comfortable clothes you already own are fine to start."
                ),
                GuideEntry(
                    question = "What should the first week feel like?",
                    answer = "Hard. Even the 60-second run intervals in Week 1 will be surprising if you haven't run before, and that's completely normal.\n\nYour cardiovascular system adapts faster than your muscles and connective tissue, so the breathlessness tends to ease before the leg fatigue does. Expect to feel tired after each session — that's the training doing its job. By Week 3 or 4 most people notice a significant improvement."
                ),
                GuideEntry(
                    question = "Is it safe to start if I haven't exercised in years?",
                    answer = "For most people, yes — C25K is specifically designed for people starting from zero. If you have a heart condition, joint problems, or haven't been active for a long time and are over 40, a brief check with your doctor first is sensible but not always necessary.\n\nThe programme starts very gently precisely because it's aimed at complete beginners."
                )
            )
        ),
        GuideSection(
            title = "During a Workout",
            entries = listOf(
                GuideEntry(
                    question = "What is a warm-up, and why shouldn't I skip it?",
                    answer = "The 5-minute brisk walk at the start of every session gradually raises your heart rate and increases blood flow to your muscles. Cold muscles are less elastic and more prone to strain.\n\nSkipping the warm-up makes the first run interval feel much harder than it should, and increases the risk of pulling something. Five minutes is a small investment."
                ),
                GuideEntry(
                    question = "What is a cool-down?",
                    answer = "The 5-minute walk at the end brings your heart rate down gradually rather than stopping abruptly. It also helps your body begin clearing lactic acid from your muscles, which reduces post-workout stiffness.\n\nIt takes only 5 minutes and makes a real difference to how you feel the following day."
                ),
                GuideEntry(
                    question = "Pain vs. discomfort — how do I tell the difference?",
                    answer = "Discomfort is normal and expected: burning lungs, tired legs, muscles that feel like they're complaining. This is what exercise feels like, especially early on.\n\nPain is different. It's sharp, localised to a specific point (a knee, a shin, a foot), or it gets worse as you continue running. Pain is your body signalling that something is wrong.\n\nDiscomfort says \"this is hard.\" Pain says \"stop.\" If you feel genuine pain during a session, stop and walk home. If it persists after a day of rest, see a physio or doctor before running again."
                ),
                GuideEntry(
                    question = "How should I breathe?",
                    answer = "There is no single correct technique — breathe however lets you take in the most air. Many runners settle naturally into a 2:2 rhythm: inhale for 2 steps, exhale for 2 steps. Others prefer 3:2 or just breathe as needed.\n\nBreathing through both your nose and mouth is perfectly fine and actually advisable when working hard. If you're gasping, you're running too fast — slow down until your breathing feels manageable."
                ),
                GuideEntry(
                    question = "What if I can't finish a run interval?",
                    answer = "Walk. There is no failure in walking — the walk breaks are built into the programme for exactly this reason.\n\nIf you regularly can't complete the run intervals, try slowing your running pace even further. Many people are surprised how slow \"slow\" needs to be at first. A very slow jog that you can sustain is far better than a fast run that forces you to stop."
                )
            )
        ),
        GuideSection(
            title = "Between Workouts",
            entries = listOf(
                GuideEntry(
                    question = "Why do I need rest days?",
                    answer = "Fitness gains happen during rest, not during the run itself. Training creates small stresses in your muscles and connective tissue; the rest days are when your body repairs those stresses and comes back slightly stronger.\n\nThe C25K schedule has 3 sessions per week for this reason. Running every day as a beginner significantly increases the risk of overuse injuries — stress fractures, shin splints, and tendon problems that can sideline you for weeks."
                ),
                GuideEntry(
                    question = "Should I repeat a day or move on?",
                    answer = "Move on, even if a session felt hard — as long as you completed it. C25K is designed with gradual enough progression that almost everyone can move forward each week.\n\nRepeat a day only if you couldn't complete the run intervals at all — for example, if you had to stop mid-interval several times (not just at the natural end of an interval). Feeling tired, slow, or out of breath is not a reason to repeat. That's just what Week 2 feels like."
                ),
                GuideEntry(
                    question = "When am I ready to start my next session?",
                    answer = "When the soreness from the previous session has mostly cleared — usually 1 to 2 days. Some muscle soreness 24–48 hours after a run is normal (this is called DOMS, delayed onset muscle soreness) and gets less severe as your body adapts over the first few weeks.\n\nIf you're still significantly sore or stiff, take an extra rest day. One extra day rarely matters; running on very tired legs increases injury risk."
                ),
                GuideEntry(
                    question = "Do I need to stretch?",
                    answer = "Light stretching after a run (not before) can help with flexibility and reduce stiffness. Focus on calves, hamstrings, hip flexors, and quads — the muscles doing most of the work.\n\nStretching before a run while your muscles are cold is less useful and can even cause strain. The warm-up walk is more effective preparation than static stretching."
                )
            )
        ),
        GuideSection(
            title = "Glossary",
            entries = listOf(
                GuideEntry(
                    question = "Aerobic fitness",
                    answer = "The capacity of your heart and lungs to deliver oxygen to your muscles during sustained exercise. C25K specifically builds aerobic fitness — the foundation of all endurance activity."
                ),
                GuideEntry(
                    question = "Interval",
                    answer = "A period of effort at a specific intensity for a set duration. Each C25K session is structured as a series of run and walk intervals — for example, eight repetitions of \"run 60 seconds, walk 90 seconds.\""
                ),
                GuideEntry(
                    question = "Pace",
                    answer = "How fast you're running, usually expressed as minutes per kilometre or mile — for example, 7:30 per km means it takes 7 minutes and 30 seconds to run one kilometre.\n\nFor most of C25K, pace doesn't matter. Completing the intervals does. Ignore pace entirely until you've finished the programme."
                ),
                GuideEntry(
                    question = "DOMS (Delayed Onset Muscle Soreness)",
                    answer = "Muscle soreness that appears 24–48 hours after exercise, caused by microscopic damage to muscle fibres as they adapt to new stress. Normal, not harmful, and gets significantly less severe after the first few weeks of training."
                ),
                GuideEntry(
                    question = "Shin splints",
                    answer = "Pain along the front or inner edge of the lower leg, common in new runners. Usually caused by doing too much too soon, or running in worn-out shoes.\n\nIf you develop shin splints, rest for 3–5 days. When you return, check your shoes and make sure your running pace isn't too fast. Persistent shin pain should be assessed by a physio."
                ),
                GuideEntry(
                    question = "RPE (Rate of Perceived Exertion)",
                    answer = "A simple 1–10 scale for how hard an effort feels, where 1 is sitting still and 10 is an all-out sprint you couldn't sustain for more than a few seconds.\n\nMost of your C25K running should feel like a 5–6: clearly working, but sustainable. Conversational pace."
                )
            )
        )
    )
}
