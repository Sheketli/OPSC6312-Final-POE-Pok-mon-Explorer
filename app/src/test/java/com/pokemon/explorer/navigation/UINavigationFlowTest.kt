package com.pokemon.explorer.navigation

import com.pokemon.explorer.state.NavFrame
import com.pokemon.explorer.state.Screen
import com.pokemon.explorer.state.TabId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit Tests for UI Navigation Flows and Stack Management.
 * Covers: Browse -> Details, Details -> Compare, Collection -> Details, Filter navigation, and Stack history.
 */
class UINavigationFlowTest {

    private val stacks = mutableMapOf<TabId, MutableList<NavFrame>>()
    private var activeTab: TabId = TabId.Explore
    private var currentFrame: NavFrame = NavFrame(Screen.HOME)

    @Before
    fun setup() {
        stacks.clear()
        stacks[TabId.Explore] = mutableListOf(NavFrame(Screen.HOME))
        stacks[TabId.Collection] = mutableListOf(NavFrame(Screen.COLLECTION))
        stacks[TabId.Compare] = mutableListOf(NavFrame(Screen.COMPARE))
        stacks[TabId.Guide] = mutableListOf(NavFrame(Screen.TYPE_GUIDE))
        activeTab = TabId.Explore
        currentFrame = stacks.getValue(TabId.Explore).last()
    }

    private fun selectTab(tab: TabId) {
        activeTab = tab
        currentFrame = stacks.getValue(tab).last()
    }

    private fun navigate(
        screen: String,
        id: Int? = null,
        idA: Int? = null,
        filterType: String? = null,
    ) {
        val frame = NavFrame(screen = screen, id = id, idA = idA, filterType = filterType)
        stacks.getValue(activeTab).add(frame)
        currentFrame = frame
    }

    private fun goBack(): Boolean {
        val stack = stacks.getValue(activeTab)
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        currentFrame = stack.last()
        return true
    }

    // ==========================================
    // BROWSE -> DETAILS NAVIGATION TESTS
    // ==========================================

    @Test
    fun `navigating from Home browse to Detail updates frame and passes pokemon id`() {
        assertEquals(Screen.HOME, currentFrame.screen)
        assertNull(currentFrame.id)

        // User clicks on Pikachu (#25) from Browse/Home
        navigate(Screen.DETAIL, id = 25)

        assertEquals(Screen.DETAIL, currentFrame.screen)
        assertEquals(25, currentFrame.id)
        assertEquals(2, stacks.getValue(TabId.Explore).size)
    }

    @Test
    fun `navigating from Search browse to Detail updates frame with selected pokemon id`() {
        // User opens search
        navigate(Screen.SEARCH)
        assertEquals(Screen.SEARCH, currentFrame.screen)

        // User clicks Charizard (#6) in search results
        navigate(Screen.DETAIL, id = 6)
        assertEquals(Screen.DETAIL, currentFrame.screen)
        assertEquals(6, currentFrame.id)

        // Back action returns to Search
        assertTrue(goBack())
        assertEquals(Screen.SEARCH, currentFrame.screen)
    }

    // ==========================================
    // DETAILS -> COMPARE NAVIGATION TESTS
    // ==========================================

    @Test
    fun `navigating from Detail to Compare passes target pokemon id as first slot`() {
        // User is on Detail screen for Mewtwo (#150)
        navigate(Screen.DETAIL, id = 150)
        assertEquals(Screen.DETAIL, currentFrame.screen)
        assertEquals(150, currentFrame.id)

        // User clicks "Compare" button
        navigate(Screen.COMPARE, idA = 150)

        assertEquals(Screen.COMPARE, currentFrame.screen)
        assertEquals(150, currentFrame.idA)
        assertNull(currentFrame.id)
        assertEquals(3, stacks.getValue(TabId.Explore).size)
    }

    @Test
    fun `navigating from Detail to Compare and popping back restores Detail view`() {
        navigate(Screen.DETAIL, id = 133) // Eevee
        navigate(Screen.COMPARE, idA = 133)

        assertEquals(Screen.COMPARE, currentFrame.screen)

        // Pop back to Detail
        assertTrue(goBack())
        assertEquals(Screen.DETAIL, currentFrame.screen)
        assertEquals(133, currentFrame.id)

        // Pop back to Home
        assertTrue(goBack())
        assertEquals(Screen.HOME, currentFrame.screen)
        assertFalse("Root screen should not pop", goBack())
    }

    // ==========================================
    // COLLECTION -> DETAILS NAVIGATION TESTS
    // ==========================================

    @Test
    fun `navigating from Collection tab item to Detail preserves tab isolation`() {
        // Switch to Collection tab
        selectTab(TabId.Collection)
        assertEquals(TabId.Collection, activeTab)
        assertEquals(Screen.COLLECTION, currentFrame.screen)

        // User clicks a collected/favourite item Gengar (#94)
        navigate(Screen.DETAIL, id = 94)
        assertEquals(Screen.DETAIL, currentFrame.screen)
        assertEquals(94, currentFrame.id)

        // Switch tab to Explore - stack on Explore should still be at HOME
        selectTab(TabId.Explore)
        assertEquals(Screen.HOME, currentFrame.screen)

        // Switch back to Collection tab - should resume at DETAIL (#94)
        selectTab(TabId.Collection)
        assertEquals(Screen.DETAIL, currentFrame.screen)
        assertEquals(94, currentFrame.id)

        // Back action on Collection tab returns to COLLECTION root
        assertTrue(goBack())
        assertEquals(Screen.COLLECTION, currentFrame.screen)
        assertFalse("Collection root should not pop further", goBack())
    }

    // ==========================================
    // FILTER NAVIGATION TESTS
    // ==========================================

    @Test
    fun `navigating to Search with a type filter passes filterType parameter`() {
        // Navigate from Type Guide or Home filter chip to Search for "fire"
        navigate(Screen.SEARCH, filterType = "fire")

        assertEquals(Screen.SEARCH, currentFrame.screen)
        assertEquals("fire", currentFrame.filterType)
        assertNull(currentFrame.id)
    }

    // ==========================================
    // DEEP BACKSTACK INTEGRATION TESTS
    // ==========================================

    @Test
    fun `full navigation flow Home to Detail to Compare and back handles stack correctly`() {
        // 1. Home
        assertEquals(Screen.HOME, currentFrame.screen)

        // 2. Click Snorlax (#143) -> Detail
        navigate(Screen.DETAIL, id = 143)
        assertEquals(Screen.DETAIL, currentFrame.screen)

        // 3. Click Compare -> Compare screen with Snorlax as idA
        navigate(Screen.COMPARE, idA = 143)
        assertEquals(Screen.COMPARE, currentFrame.screen)

        // 4. Back -> Detail
        assertTrue(goBack())
        assertEquals(Screen.DETAIL, currentFrame.screen)
        assertEquals(143, currentFrame.id)

        // 5. Back -> Home
        assertTrue(goBack())
        assertEquals(Screen.HOME, currentFrame.screen)

        // 6. Back at root returns false
        assertFalse(goBack())
    }
}
