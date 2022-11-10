/*
 * Nextcloud Android client application
 *
 *  @author Álvaro Brey
 *  Copyright (C) 2022 Álvaro Brey
 *  Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.ui.fileactions

import androidx.annotation.IdRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.client.account.CurrentAccountProvider
import com.nextcloud.utils.TimeConstants
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.files.FileMenuFilter
import com.owncloud.android.lib.resources.files.model.FileLockType
import com.owncloud.android.ui.activity.ComponentsGetter
import javax.inject.Inject

class FileActionsViewModel @Inject constructor(
    private val currentAccountProvider: CurrentAccountProvider,
    private val filterFactory: FileMenuFilter.Factory
) :
    ViewModel() {

    data class LockInfo(val lockType: FileLockType, val lockedBy: String, val lockedUntil: Long?)

    sealed interface UiState {
        object Loading : UiState
        data class LoadedForSingleFile(
            val actions: List<FileAction>,
            val titleFile: OCFile?,
            val lockInfo: LockInfo? = null
        ) : UiState

        data class LoadedForMultipleFiles(val actions: List<FileAction>, val fileCount: Int) : UiState
    }

    private val _uiState: MutableLiveData<UiState> = MutableLiveData(UiState.Loading)
    val uiState: LiveData<UiState>
        get() = _uiState

    private val _clickActionId: MutableLiveData<Int?> = MutableLiveData(null)
    val clickActionId: LiveData<Int?>
        @IdRes
        get() = _clickActionId

    fun load(
        files: Collection<OCFile>,
        componentsGetter: ComponentsGetter,
        numberOfAllFiles: Int?,
        isOverflow: Boolean?,
        additionalFilter: IntArray?
    ) {
        val toHide = filterFactory.newInstance(
            numberOfAllFiles ?: 1,
            files.toList(),
            componentsGetter,
            isOverflow ?: false,
            currentAccountProvider.user
        )
            .getToHide(false)
        val availableActions = FileAction.SORTED_VALUES
            .filter { additionalFilter == null || it.id !in additionalFilter }
            .filter { it.id !in toHide }
        _uiState.value = when (files.size) {
            1 -> {
                val file = files.first()
                UiState.LoadedForSingleFile(availableActions, file, getLockInfo(file))
            }
            else -> UiState.LoadedForMultipleFiles(availableActions, files.size)
        }
    }

    private fun getLockInfo(file: OCFile): LockInfo? {
        val lockType = file.lockType
        val username = file.lockOwnerDisplayName ?: file.lockOwnerId
        return if (file.isLocked && lockType != null && username != null) {
            LockInfo(lockType, username, getLockedUntil(file))
        } else {
            null
        }
    }

    private fun getLockedUntil(file: OCFile): Long? {
        return if (file.lockTimestamp == 0L || file.lockTimeout == 0L) {
            null
        } else {
            (file.lockTimestamp + file.lockTimeout) * TimeConstants.MILLIS_PER_SECOND
        }
    }

    fun onClick(action: FileAction) {
        _clickActionId.value = action.id
    }
}
