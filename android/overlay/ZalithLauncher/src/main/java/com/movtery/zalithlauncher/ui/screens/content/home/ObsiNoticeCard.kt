/*
 * ObsiLauncher - modified version of ZalithLauncher 2
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.components.BackgroundCard

/**
 * Permanent home-screen notice required by the additional terms of ZalithLauncher 2
 * (GPLv3 section 7): every modified version must say that it is an
 * "Unofficial Modified Version" on its main interface and keep the original credits.
 */
object ObsiNoticeCard {
    fun create() = SystemCard(id = "system_obsi_notice") {
        BackgroundCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.obsi_notice_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.obsi_notice_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = stringResource(R.string.obsi_notice_credit),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
