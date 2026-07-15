package com.hackerapps.c2k.ui.screen.contributors

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackerapps.c2k.R

private data class Contributor(
    val name: String,
    val github: String? = null,
    val fediverse: String? = null,
    @StringRes val contributionRes: List<Int>
)

private val contributors = listOf(
    Contributor(
        name = "xmgz",
        github = "xmgz",
        fediverse = "@l10n@gts.xmgz.eu",
        contributionRes = listOf(R.string.contributor_translation_es, R.string.contributor_translation_gl)
    ),
    Contributor(
        name = "Andrew Farabee",
        github = "andrewfarabee",
        contributionRes = listOf(R.string.contributor_fix_tts_ducking)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributorsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contributors_title)) },
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

            items(contributors) { contributor ->
                ContributorCard(contributor)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ContributorCard(contributor: Contributor) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                contributor.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            contributor.github?.let {
                Spacer(Modifier.height(2.dp))
                HandleRow(label = stringResource(R.string.contributor_github_label), value = it)
            }
            contributor.fediverse?.let {
                Spacer(Modifier.height(2.dp))
                HandleRow(label = stringResource(R.string.contributor_fediverse_label), value = it)
            }
            Spacer(Modifier.height(8.dp))
            contributor.contributionRes.forEachIndexed { i, resId ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(resId),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HandleRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
