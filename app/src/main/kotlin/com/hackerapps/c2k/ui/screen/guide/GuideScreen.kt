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
    val sections = guideSections()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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
                contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
private fun guideSections(): List<GuideSection> = listOf(
    GuideSection(
        title = stringResource(R.string.guide_section_before_start),
        entries = listOf(
            GuideEntry(stringResource(R.string.guide_q_conversational_pace), stringResource(R.string.guide_a_conversational_pace)),
            GuideEntry(stringResource(R.string.guide_q_gear_needed),         stringResource(R.string.guide_a_gear_needed)),
            GuideEntry(stringResource(R.string.guide_q_first_week),          stringResource(R.string.guide_a_first_week)),
            GuideEntry(stringResource(R.string.guide_q_safe_to_start),       stringResource(R.string.guide_a_safe_to_start)),
        )
    ),
    GuideSection(
        title = stringResource(R.string.guide_section_during_workout),
        entries = listOf(
            GuideEntry(stringResource(R.string.guide_q_warmup),              stringResource(R.string.guide_a_warmup)),
            GuideEntry(stringResource(R.string.guide_q_cooldown),            stringResource(R.string.guide_a_cooldown)),
            GuideEntry(stringResource(R.string.guide_q_pain_discomfort),     stringResource(R.string.guide_a_pain_discomfort)),
            GuideEntry(stringResource(R.string.guide_q_breathing),           stringResource(R.string.guide_a_breathing)),
            GuideEntry(stringResource(R.string.guide_q_cant_finish_interval),stringResource(R.string.guide_a_cant_finish_interval)),
        )
    ),
    GuideSection(
        title = stringResource(R.string.guide_section_between_workouts),
        entries = listOf(
            GuideEntry(stringResource(R.string.guide_q_rest_days),           stringResource(R.string.guide_a_rest_days)),
            GuideEntry(stringResource(R.string.guide_q_repeat_day),          stringResource(R.string.guide_a_repeat_day)),
            GuideEntry(stringResource(R.string.guide_q_ready_next_session),  stringResource(R.string.guide_a_ready_next_session)),
            GuideEntry(stringResource(R.string.guide_q_stretch),             stringResource(R.string.guide_a_stretch)),
        )
    ),
    GuideSection(
        title = stringResource(R.string.guide_section_glossary),
        entries = listOf(
            GuideEntry(stringResource(R.string.guide_q_aerobic_fitness),  stringResource(R.string.guide_a_aerobic_fitness)),
            GuideEntry(stringResource(R.string.guide_q_interval_term),    stringResource(R.string.guide_a_interval_term)),
            GuideEntry(stringResource(R.string.guide_q_pace_term),        stringResource(R.string.guide_a_pace_term)),
            GuideEntry(stringResource(R.string.guide_q_doms),             stringResource(R.string.guide_a_doms)),
            GuideEntry(stringResource(R.string.guide_q_shin_splints),     stringResource(R.string.guide_a_shin_splints)),
            GuideEntry(stringResource(R.string.guide_q_rpe),              stringResource(R.string.guide_a_rpe)),
        )
    ),
)
