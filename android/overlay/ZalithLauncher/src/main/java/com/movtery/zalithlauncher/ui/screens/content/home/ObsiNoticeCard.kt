/*
 * ObsiLauncher home notice card (glass style)
 * Copyright (C) 2026 ObsiFox Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.movtery.zalithlauncher.ui.screens.content.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.components.BackgroundCard

/**
 * Permanent home-screen notice required by the additional terms of the upstream
 * license (GPLv3 section 7): every modified version must say that it is an
 * "Unofficial Modified Version" on its main interface.
 *
 * v2 restyle: warm dark glass, fox mark, single accent line — matching the
 * ObsiLauncher desktop redesign. Credit text now only mentions MobileGlues.
 */
object ObsiNoticeCard {
    fun create() = SystemCard(id = "system_obsi_notice") {
        GlassNoticeCard()
    }
}

@Composable
private fun GlassNoticeCard() {
    val scheme = MaterialTheme.colorScheme
    BackgroundCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.primary.copy(alpha = 0.10f),
                            scheme.surface.copy(alpha = 0.0f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = stringResource(R.string.obsi_notice_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.primary
                    )
                    Text(
                        modifier = Modifier.alpha(0.75f),
                        text = stringResource(R.string.obsi_notice_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                modifier = Modifier.alpha(0.65f),
                text = stringResource(R.string.obsi_notice_credit),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
