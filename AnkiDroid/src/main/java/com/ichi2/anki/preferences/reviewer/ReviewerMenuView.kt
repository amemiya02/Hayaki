// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.reviewer

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.ActionMenuView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.children
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.utils.ext.setIconRes
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.foundation
import com.ichi2.compose.ui.dialogs.PaperDialog
import com.ichi2.compose.ui.dialogs.createPaperDialog
import kotlinx.coroutines.launch
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.android.R as CommonR

/** Compose Card actions backed by the reviewer’s configurable Android menu model. */
class ReviewerMenuView(
    context: Context,
) : AbstractComposeView(context) {
    private var menuVersion by mutableIntStateOf(0)

    /** Resolves the menu's icons, so the monochrome ones take the text colour */
    private val iconContext = ContextThemeWrapper(context, R.style.ThemeOverlay_Hayaki_ReviewerMenu)
    private val frontMenu = newMenu()
    private val overflowMenu = newMenu()
    private var onMenuItemClickListener: ActionMenuView.OnMenuItemClickListener? = null
    private var openedMenu by mutableStateOf<Menu?>(null)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val menuItems =
            ReviewerMenuRepository(
                context.sharedPrefs(),
            ).getActionsByMenuDisplayTypes(MenuDisplayType.ALWAYS, MenuDisplayType.MENU_ONLY)
        addActions(menuItems.getValue(MenuDisplayType.ALWAYS), menuItems.getValue(MenuDisplayType.MENU_ONLY))
    }

    private fun newMenu() =
        object : MenuBuilder(iconContext) {
            override fun onItemsChanged(structureChanged: Boolean) {
                super.onItemsChanged(structureChanged)
                menuVersion++
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch { setFlagTitles() }
    }

    @Composable
    override fun Content() {
        AnkiDroidTheme {
            val version = menuVersion
            Row(Modifier.widthIn(max = 192.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())) {
                    for (item in frontMenu.children.filter(MenuItem::isVisible)) {
                        IconButton(
                            onClick = { select(item) },
                            enabled = item.isEnabled,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = item.title.toString() },
                        ) {
                            MenuIcon(item, version)
                        }
                    }
                }
                Box {
                    if (overflowMenu.hasVisibleItems()) {
                        IconButton(onClick = { openedMenu = overflowMenu }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painterResource(CommonR.drawable.symbol_more_vert),
                                stringResource(AppCompatR.string.abc_action_menu_overflow_description),
                                tint = MaterialTheme.foundation.colors.onBackground,
                            )
                        }
                    }
                    val opened = openedMenu
                    // Dispose the popup with its items so it cannot be remeasured during dismissal.
                    if (opened != null) {
                        DropdownMenu(expanded = true, onDismissRequest = { openedMenu = null }) {
                            for (item in opened.children.filter(MenuItem::isVisible)) {
                                MenuRow(item, version) { select(item) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun clear() {
        frontMenu.clear()
        overflowMenu.clear()
    }

    fun isEmpty() = frontMenu.size() == 0 && overflowMenu.size() == 0

    fun findItem(id: Int): MenuItemImpl? = (frontMenu.findItem(id) ?: overflowMenu.findItem(id)) as? MenuItemImpl

    fun setOnMenuItemClickListener(listener: ActionMenuView.OnMenuItemClickListener) {
        onMenuItemClickListener = listener
    }

    /** Also supports keyboard and gesture shortcuts when the Card Template hides the app bar. */
    fun showOptionsMenu(): Boolean {
        if (isShown && overflowMenu.hasVisibleItems()) {
            openedMenu = overflowMenu
            return true
        }
        val items = (frontMenu.children + overflowMenu.children).filter(MenuItem::isVisible).toList()
        if (items.isEmpty()) return false
        createPaperDialog(context) { dialog ->
            var available by androidx.compose.runtime.remember { mutableStateOf(items) }
            PaperDialog(
                title = stringResource(AppCompatR.string.abc_action_menu_overflow_description),
                onDismissRequest = dialog::dismiss,
            ) {
                Column {
                    for (item in available) {
                        MenuRow(item, menuVersion) {
                            val submenu = item.subMenu
                            if (submenu == null) {
                                dialog.dismiss()
                                onMenuItemClickListener?.onMenuItemClick(item)
                            } else {
                                available = submenu.children.filter(MenuItem::isVisible).toList()
                            }
                        }
                    }
                }
            }
        }.show()
        return true
    }

    private fun select(item: MenuItem) {
        openedMenu = item.subMenu
        if (!item.hasSubMenu()) onMenuItemClickListener?.onMenuItemClick(item)
    }

    @Composable
    private fun MenuRow(
        item: MenuItem,
        version: Int,
        activate: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(item.title.toString()) },
            onClick = activate,
            enabled = item.isEnabled,
            leadingIcon = item.icon?.let { { MenuIcon(item, version) } },
        )
    }

    @Composable
    private fun MenuIcon(
        item: MenuItem,
        version: Int,
    ) {
        val icon = androidx.compose.runtime.remember(item, version) { item.icon?.toBitmap()?.asImageBitmap() }
        val alpha = if (item.isEnabled) 1f else DISABLED_ALPHA
        icon?.let { Image(it, contentDescription = null, Modifier.size(24.dp).alpha(alpha)) }
    }

    fun addActions(
        alwaysShow: List<ViewerAction>,
        menuOnly: List<ViewerAction>,
    ) {
        addActionsToMenu(frontMenu, alwaysShow, MenuItem.SHOW_AS_ACTION_ALWAYS)
        addActionsToMenu(overflowMenu, menuOnly, MenuItem.SHOW_AS_ACTION_NEVER)

        for (action in ViewerAction.entries.filter { it.parentMenu != null }) {
            val subMenu = findItem(action.parentMenu!!.menuId)?.subMenu ?: continue
            subMenu.add(Menu.NONE, action.menuId, Menu.NONE, action.title(context)).apply {
                action.drawableRes?.let { setIconRes(iconContext, it) }
            }
        }
    }

    suspend fun setFlagTitles() {
        val submenu = findItem(R.id.action_flag)?.subMenu ?: return
        for ((flag, name) in Flag.queryDisplayNames(context)) {
            submenu.findItem(flag.id)?.title = name
        }
        menuVersion++
    }

    private fun addActionsToMenu(
        menu: Menu,
        actions: List<ViewerAction>,
        menuActionType: Int,
    ) {
        val subMenus = ViewerAction.getSubMenus()
        for (action in actions) {
            val title = action.title(context)
            val item =
                if (action in subMenus) {
                    menu.addSubMenu(Menu.NONE, action.menuId, Menu.NONE, title).item
                } else {
                    menu.add(Menu.NONE, action.menuId, Menu.NONE, title)
                }
            action.drawableRes?.let { item.setIconRes(iconContext, it) }
            item.setShowAsAction(menuActionType)
        }
    }
}

/** Material's opacity for disabled content */
private const val DISABLED_ALPHA = 0.38f
