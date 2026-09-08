package me.tshine.easymarksync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tshine.easymarksync.data.repo.NoteRepository
import me.tshine.easymarksync.data.repo.SyncRepository
import me.tshine.easymarksync.ui.editor.EditorScreen
import me.tshine.easymarksync.ui.editor.EditorViewModel
import me.tshine.easymarksync.ui.notes.NoteListScreen
import me.tshine.easymarksync.ui.notes.NoteListViewModel
import me.tshine.easymarksync.ui.preview.PreviewScreen
import me.tshine.easymarksync.ui.settings.SettingsScreen
import me.tshine.easymarksync.ui.settings.SettingsViewModel
import me.tshine.easymarksync.ui.theme.EasyMarkSyncTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { (application as EasyMarkApp).database }
    private val noteRepository by lazy { NoteRepository(database) }
    private val syncRepository by lazy { SyncRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMarkSyncTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "notes") {

                    composable("notes") {
                        val vm: NoteListViewModel = viewModel(factory = viewModelFactory {
                            initializer { NoteListViewModel(noteRepository, syncRepository) }
                        })
                        NoteListScreen(
                            viewModel = vm,
                            onOpenNote = { id -> navController.navigate("editor/$id") },
                            onCreateNote = { id -> navController.navigate("editor/$id") }
                        )
                    }

                    composable("editor/{noteId}") { entry ->
                        val noteId = entry.arguments?.getString("noteId") ?: return@composable
                        val vm: EditorViewModel = viewModel(
                            key = "editor_$noteId",
                            factory = viewModelFactory {
                                initializer { EditorViewModel(noteRepository, noteId) }
                            }
                        )
                        EditorScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onPreview = { id -> navController.navigate("preview/$id") },
                            onDeleted = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("preview/{noteId}") { entry ->
                        val noteId = entry.arguments?.getString("noteId") ?: return@composable
                        var markdown by remember { mutableStateOf("") }
                        LaunchedEffect(noteId) {
                            markdown = withContext(Dispatchers.IO) {
                                noteRepository.getById(noteId)?.content ?: ""
                            }
                        }
                        PreviewScreen(
                            markdown = markdown,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
                            initializer { SettingsViewModel(syncRepository) }
                        })
                        SettingsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
