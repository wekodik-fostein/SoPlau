package com.soplau.rp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.soplau.rp.AppContainer
import com.soplau.rp.data.db.CharacterEntity
import com.soplau.rp.ui.character.CharacterEditScreen
import com.soplau.rp.ui.character.CharacterEditViewModel
import com.soplau.rp.ui.chat.ChatScreen
import com.soplau.rp.ui.chat.ChatViewModel
import com.soplau.rp.ui.settings.SettingsScreen
import com.soplau.rp.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{chatId}"
    const val CHARACTER = "character/{characterId}"
    const val SETTINGS = "settings"

    fun chat(chatId: Long) = "chat/$chatId"
    fun character(characterId: Long) = "character/$characterId"
}

@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val mainVm: MainViewModel = viewModel(factory = MainViewModel.Factory)

    val backStack by nav.currentBackStackEntryAsState()
    val activeChatId = if (backStack?.destination?.route == Routes.CHAT) {
        backStack?.arguments?.getLong("chatId")
    } else {
        null
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CharacterDrawerContent(
                viewModel = mainVm,
                activeChatId = activeChatId,
                onOpenChat = { chatId -> nav.navigate(Routes.chat(chatId)) },
                onNewCharacter = { nav.navigate(Routes.character(0)) },
                onEditCharacter = { id -> nav.navigate(Routes.character(id)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onCloseDrawer = ::closeDrawer
            )
        }
    ) {
        NavHost(navController = nav, startDestination = Routes.HOME) {

            composable(Routes.HOME) {
                HomeEmpty(onCreate = { nav.navigate(Routes.character(0)) }, onOpenDrawer = {
                    scope.launch { drawerState.open() }
                })
            }

            composable(
                Routes.CHAT,
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { entry ->
                val chatId = entry.arguments!!.getLong("chatId")
                val chatVm: ChatViewModel = viewModel(
                    key = "chat-$chatId",
                    factory = ChatViewModel.factory(chatId)
                )
                val character by produceState<CharacterEntity?>(null, chatId) {
                    value = container.repository.getChat(chatId)?.let {
                        container.repository.getCharacter(it.characterId)
                    }
                }
                ChatScreen(
                    viewModel = chatVm,
                    character = character,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }

            composable(
                Routes.CHARACTER,
                arguments = listOf(navArgument("characterId") { type = NavType.LongType })
            ) { entry ->
                val characterId = entry.arguments!!.getLong("characterId")
                val editVm: CharacterEditViewModel = viewModel(
                    key = "character-$characterId",
                    factory = CharacterEditViewModel.factory(characterId)
                )
                CharacterEditScreen(
                    viewModel = editVm,
                    onBack = { nav.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory())
                SettingsScreen(viewModel = settingsVm, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun HomeEmpty(onCreate: () -> Unit, onOpenDrawer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.soplau.rp.ui.theme.LocalAppBackground.current)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SoPlau", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Создай персонажа или открой чат в меню.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onCreate, modifier = Modifier.padding(top = 16.dp)) {
            Text("Создать персонажа")
        }
        Button(onClick = onOpenDrawer, modifier = Modifier.padding(top = 8.dp)) {
            Text("Открыть меню")
        }
    }
}
