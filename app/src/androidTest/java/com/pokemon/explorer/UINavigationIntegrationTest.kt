package com.pokemon.explorer

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokemon.explorer.databinding.FragmentCollectionBinding
import com.pokemon.explorer.databinding.FragmentCompareBinding
import com.pokemon.explorer.databinding.FragmentDetailBinding
import com.pokemon.explorer.databinding.FragmentHomeBinding
import com.pokemon.explorer.databinding.FragmentSearchBinding
import com.pokemon.explorer.ui.fragments.CompareFragment
import com.pokemon.explorer.ui.fragments.DetailFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Instrumentation UI and Navigation Tests.
 * Tests UI binding inflation, view hierarchies, fragment instance arguments, filter setups, loading indicators, and error states.
 */
@RunWith(AndroidJUnit4::class)
class UINavigationIntegrationTest {

    private val context: Context
        get() = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_PokemonExplorer,
        )

    // ==========================================
    // BROWSE -> DETAILS UI & NAVIGATION TESTS
    // ==========================================

    @Test
    fun homeBrowseLayoutInflatesWithSearchAndFeaturedNavigationControls() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)
            val binding = FragmentHomeBinding.inflate(inflater)

            assertNotNull("Search bar button must be present on Home", binding.btnSearchBar)
            assertNotNull("See All button must be present on Home", binding.btnSeeAll)
            assertNotNull("Featured details button must be present on Home", binding.btnViewDetails)
            assertNotNull("Random Pokémon button must be present on Home", binding.btnRandom)
            assertNotNull("Filter chips RecyclerView must be present", binding.rvFilterChips)
            assertNotNull("Rarity filter chips RecyclerView must be present", binding.rvRarityFilterChips)
            assertNotNull("Pokémon grid RecyclerView must be present", binding.rvPokemonGrid)
        }
    }

    @Test
    fun detailFragmentNewInstanceInstantiatesWithCorrectPokemonIdBundle() {
        val detailFragment = DetailFragment.newInstance(25)
        val args = detailFragment.arguments
        assertNotNull("Arguments bundle should not be null", args)
        assertEquals( 25, args?.getInt("pokemon_id"))
    }

    @Test
    fun detailLayoutInflatesWithArtworkAndActionButtons() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)
            val binding = FragmentDetailBinding.inflate(inflater)

            assertNotNull("Back button must exist on Detail page", binding.btnBack)
            assertNotNull("Favourite heart button must exist", binding.btnFavourite)
            assertNotNull("Compare header button must exist", binding.btnCompare)
            assertNotNull("Artwork ImageView must exist", binding.ivDetailArtwork)
            assertNotNull("Shiny toggle button group must exist", binding.toggleShiny)
            assertNotNull("Action favourite button must exist", binding.btnActionFavourite)
            assertNotNull("Action catch button must exist", binding.btnActionCatch)
            assertNotNull("Action compare button must exist", binding.btnActionCompare)
        }
    }

    // ==========================================
    // DETAILS -> COMPARE UI & NAVIGATION TESTS
    // ==========================================

    @Test
    fun compareFragmentNewInstanceInstantiatesWithCorrectSideIds() {
        val compareFragment = CompareFragment.newInstance(idA = 150, idB = 6)
        val args = compareFragment.arguments
        assertNotNull("Arguments bundle should not be null", args)
        assertEquals(150, args?.getInt("idA"))
        assertEquals(6, args?.getInt("idB"))
    }

    @Test
    fun compareLayoutInflatesWithBothPokemonPickersAndResultViews() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)
            val binding = FragmentCompareBinding.inflate(inflater)

            assertNotNull("Back button must exist on Compare page", binding.btnBack)
            assertNotNull("Side A artwork must exist", binding.ivArtworkA)
            assertNotNull("Side B artwork must exist", binding.ivArtworkB)
            assertNotNull("Picker A input field must exist", binding.etPickerA)
            assertNotNull("Picker B input field must exist", binding.etPickerB)
            assertNotNull("Pick A submit button must exist", binding.btnPickA)
            assertNotNull("Pick B submit button must exist", binding.btnPickB)
            assertNotNull("Comparison rows container must exist", binding.llCompareRows)
            assertNotNull("Comparison result TextView must exist", binding.tvComparisonResult)
            assertNotNull("Prediction basis TextView must exist", binding.tvPredictionBasis)
        }
    }

    // ==========================================
    // COLLECTION ITEM -> DETAILS UI TESTS
    // ==========================================

    @Test
    fun collectionLayoutInflatesWithTabToggleProgressAndGrid() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)
            val binding = FragmentCollectionBinding.inflate(inflater)

            assertNotNull("Collection tab toggle group must exist", binding.toggleCollectionTab)
            assertNotNull("Progress ring ProgressBar must exist", binding.pbCollectionRing)
            assertNotNull("Progress percent TextView must exist", binding.tvProgressPercent)
            assertNotNull("Caught count TextView must exist", binding.tvCaughtCount)
            assertNotNull("Trade hub button must exist", binding.btnOpenTradeHub)
            assertNotNull("Collection grid RecyclerView must exist", binding.rvCollectionGrid)
            assertNotNull("Empty state layout must exist", binding.tvCollectionEmpty)
        }
    }

    // ==========================================
    // FILTERS & CHIPS UI TESTS
    // ==========================================

    @Test
    fun searchLayoutInflatesWithPopularAndRecentFilterChips() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)
            val binding = FragmentSearchBinding.inflate(inflater)

            assertNotNull("Search input field must exist", binding.etSearchQuery)
            assertNotNull("Back button must exist on Search page", binding.btnBack)
            assertNotNull("Popular searches ChipGroup must exist", binding.cgPopularSearches)
            assertNotNull("Recent searches ChipGroup must exist", binding.cgRecentSearches)
            assertNotNull("Search results RecyclerView must exist", binding.rvSearchResults)
        }
    }

    // ==========================================
    // LOADING & ERROR STATES UI TESTS
    // ==========================================

    @Test
    fun verifyLoadingIndicatorsExistInAllMainLayouts() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)
            val home = FragmentHomeBinding.inflate(inflater)
            val detail = FragmentDetailBinding.inflate(inflater)
            val compare = FragmentCompareBinding.inflate(inflater)
            val collection = FragmentCollectionBinding.inflate(inflater)
            val search = FragmentSearchBinding.inflate(inflater)

            assertNotNull("Home loading indicator must exist", home.pbLoading)
            assertNotNull("Detail loading indicator must exist", detail.pbDetailLoading)
            assertNotNull("Compare layout must inflate", compare.root)
            assertNotNull("Collection progress ring must exist", collection.pbCollectionRing)
            assertNotNull("Search progress bar must exist", search.pbSearching)
        }
    }

    @Test
    fun verifyErrorAndEmptyStateStringsAreDefined() {
        val emptyCaughtText = context.getString(R.string.empty_caught)
        val compareErrorText = context.getString(R.string.comparison_load_error)
        val noFavsText = context.getString(R.string.ui_no_favourites_yet_ntap_the_heart_icon_on_any_pok_mon_to_add_it_here)

        assertTrue("Empty caught string should describe state", emptyCaughtText.contains("No Pokémon Caught"))
        assertTrue("Compare error string should describe connection issue", compareErrorText.contains("Check your connection"))
        assertTrue("No favourites string should instruct user", noFavsText.contains("heart icon"))
    }
}
