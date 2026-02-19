package com.enrpau.dualscreendex

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainScreenController(
    private val context: Context,
    private val root: View,
    private val viewModel: MainViewModel,
    private val isOverlay: Boolean = false
) {
    
    private var currentScale: Float = 1f
    private var currentWidthPx: Int = 960
    private var isDimmed: Boolean = false




    // These may not exist in overlay layout → nullable
    private val tvName: TextView? = root.findViewById(R.id.tvTargetName)
    private val tvId: TextView? = root.findViewById(R.id.tvTargetId)
    private val layoutTypes: LinearLayout? = root.findViewById(R.id.layoutTargetTypes)
    private val gridWeak: LinearLayout? = root.findViewById(R.id.gridWeak)
    private val gridResist: LinearLayout? = root.findViewById(R.id.gridResist)
    private val lblWeak: TextView? = root.findViewById(R.id.lblWeak)
    private val lblResist: TextView? = root.findViewById(R.id.lblResist)
    private val cardHeader: View? = root.findViewById(R.id.cardHeader)
    private val cardData: CardView? = root.findViewById(R.id.cardData)

    private val rvList: RecyclerView? = root.findViewById(R.id.rvPokemonList)
    private val containerBattle: View? = root.findViewById(R.id.containerBattle)
    private val containerPokedex: View? = root.findViewById(R.id.containerPokedex)

    private lateinit var adapter: PokemonAdapter

    init {
        setupRecycler()
        attachObservers()
    }

    // --------------------------------------------------
    // Recycler (only exists in main activity)
    // --------------------------------------------------

    private fun setupRecycler() {
        rvList?.layoutManager = LinearLayoutManager(context)

        adapter = PokemonAdapter(emptyList()) { selectedPokemon ->
            viewModel.onPokemonSelectedFromList(selectedPokemon)
        }

        rvList?.adapter = adapter
    }

    // --------------------------------------------------
    // Observers
    // --------------------------------------------------

    private fun attachObservers() {

        viewModel.displayedPokemon.observeForever { pokemon ->

            if (pokemon != null) {
                updateCardUI(pokemon)

                if (isOverlay) {
                    scaleUI(currentWidthPx)
                }
            }
        }




        viewModel.pokedexList.observeForever {
            adapter.updateList(it)
        }

        viewModel.isBattleMode.observeForever {
            containerPokedex?.visibility = if (it) View.GONE else View.VISIBLE
            containerBattle?.visibility = if (it) View.VISIBLE else View.GONE
        }

        viewModel.weaknessList.observeForever {
            gridWeak?.let { container ->
                populateSmartGrid(container, it)

                if (isOverlay) {
                    scaleGrid(container, (if (isOverlay) 9f else 12f) * currentScale)
                }
            }

            lblWeak?.visibility = if (it.isNotEmpty()) View.VISIBLE else View.GONE
        }


        viewModel.resistanceList.observeForever {
            gridResist?.let { container ->
                populateSmartGrid(container, it)

                if (isOverlay) {
                    scaleGrid(container, (if (isOverlay) 9f else 12f) * currentScale)
                }
            }

            lblResist?.visibility = if (it.isNotEmpty()) View.VISIBLE else View.GONE
        }

    }

    // --------------------------------------------------
    // Scaling
    // --------------------------------------------------

    fun scaleUI(currentWidth: Int) {

        currentWidthPx = currentWidth  

        val baseWidth = 960f
        val scale = currentWidth / baseWidth
        currentScale = scale


        val nameSize = if (isOverlay) 14f else 20f
        val idSize = if (isOverlay) 9f else 14f
        val labelSize = if (isOverlay) 10f else 14f
        val badgeSize = if (isOverlay) 9f else 12f

        tvName?.textSize = nameSize * scale
        tvId?.textSize = idSize * scale
        val labelPx = labelSize * scale * context.resources.displayMetrics.scaledDensity
        lblWeak?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelPx)
        lblResist?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelPx)


        if (isOverlay) {

            tvId?.visibility = View.GONE

            root.findViewById<View?>(R.id.navLeftContainer)?.visibility = View.GONE
            root.findViewById<View?>(R.id.navRightContainer)?.visibility = View.GONE

            cardHeader?.setPadding(
                (6 * scale).toInt(),
                (4 * scale).toInt(),
                (6 * scale).toInt(),
                (4 * scale).toInt()
            )

            if (cardHeader?.layoutParams is ViewGroup.MarginLayoutParams) {
                val params = cardHeader.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = (6 * scale).toInt()
                cardHeader.layoutParams = params
            }

            

            
            lblWeak?.setTextColor(Color.BLACK)
            lblResist?.setTextColor(Color.BLACK)
        }


        layoutTypes?.let { scaleTypeBadges(it, badgeSize * scale) }
        gridWeak?.let { scaleGrid(it, badgeSize * scale) }
        gridResist?.let { scaleGrid(it, badgeSize * scale) }


    }

    private fun scaleTypeBadges(container: LinearLayout, size: Float) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is TextView) {
                child.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * context.resources.displayMetrics.scaledDensity)

                child.setPadding(
                    0,
                    (size * 0.6f).toInt(),
                    0,
                    (size * 0.6f).toInt()
                )
            }
        }
    }


    private fun scaleGrid(container: LinearLayout, size: Float) {
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val badge = row.getChildAt(j)
                val tvType = badge.findViewById<TextView>(R.id.tvBadgeType)
                val tvMult = badge.findViewById<TextView>(R.id.tvBadgeMult)
                val px = size * context.resources.displayMetrics.scaledDensity

                tvType?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
                tvMult?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)

            }
        }
    }

    // --------------------------------------------------
    // UI Update
    // --------------------------------------------------

    private fun updateCardUI(pokemon: Pokemon) {

        val theme = ThemeManager.currentTheme
        val cleanName = pokemon.name.replaceFirstChar { it.uppercase() }

        tvName?.text = cleanName
        tvId?.text = String.format("#%03d", pokemon.id)

        tvName?.setTextColor(theme.headerTextColor)
        tvId?.setTextColor(theme.subTextColor)

        val headerColor =
            if (theme.id == "oled") Color.BLACK else theme.windowBackground

        if (cardHeader is CardView) {
            cardHeader.setCardBackgroundColor(headerColor)
            cardHeader.radius = theme.cardCornerRadius
        }

        cardData?.radius = theme.cardCornerRadius

        layoutTypes?.removeAllViews()
        layoutTypes?.let {
            addTypeBadge(it, pokemon.type1)

            if (pokemon.type2 != null && pokemon.type2 != PokemonType.UNKNOWN) {
                addTypeBadge(it, pokemon.type2)
            }
        }

        if (isOverlay) {
            layoutTypes?.let {
                scaleTypeBadges(it, (if (isOverlay) 9f else 12f) * currentScale)
            }
        }

    }

    // --------------------------------------------------
    // Weakness Grid
    // --------------------------------------------------

    private fun populateSmartGrid(
        container: LinearLayout,
        list: List<MainActivity.MatchupData>
    ) {
        container.removeAllViews()

        val rows = list.chunked(3)

        for (rowItems in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = rowItems.size.toFloat()
            }

            for (data in rowItems) {
                val badge = createWeaknessBadgeView(data.type, data.multiplier)

                rowLayout.addView(
                    badge,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }

            container.addView(rowLayout)
        }
    }

    private fun createWeaknessBadgeView(
        type: PokemonType,
        mult: Double
    ): View {

        val view = LayoutInflater.from(context)
            .inflate(R.layout.badge_weakness, null, false)

        view.findViewById<TextView>(R.id.tvBadgeType).apply {
            text = type.displayName
            setBackgroundColor(type.colorHex)
        }

        view.findViewById<TextView>(R.id.tvBadgeMult).apply {
            text = "× ${
                when (mult) {
                    0.5 -> "½"
                    0.25 -> "¼"
                    0.0 -> "0"
                    else -> mult.toInt().toString()
                }
            }"
            setBackgroundColor(type.colorHex)
        }

        return view
    }

    private fun addTypeBadge(container: LinearLayout, type: PokemonType) {

        val baseSize = if (isOverlay) 9f else 12f
        val px = baseSize * currentScale *
                context.resources.displayMetrics.scaledDensity

        val tv = TextView(context).apply {
            text = type.displayName.uppercase()
            setTextColor(Color.WHITE)
            setBackgroundColor(type.colorHex)
            gravity = android.view.Gravity.CENTER

            // 🔥 FORCE PX size
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
        }

        container.addView(
            tv,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        )
    }

    fun setOverlayDimmed(dim: Boolean) {

        if (!isOverlay) return

        isDimmed = dim

        val textAlpha = if (dim) 0f else 1f
        val backgroundAlpha = if (dim) 25 else 128
        // 25 ≈ 90% transparent
        // 128 ≈ 50% transparent

        // Fade main text
        tvName?.alpha = textAlpha
        tvId?.alpha = textAlpha
        lblWeak?.alpha = textAlpha
        lblResist?.alpha = textAlpha

        // Fade type badges
        layoutTypes?.let {
            for (i in 0 until it.childCount) {
                it.getChildAt(i).alpha = textAlpha
            }
        }

        // Fade weakness/resistance grid
        gridWeak?.let { container ->
            for (i in 0 until container.childCount) {
                val row = container.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until row.childCount) {
                    row.getChildAt(j).alpha = textAlpha
                }
            }
        }

        gridResist?.let { container ->
            for (i in 0 until container.childCount) {
                val row = container.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until row.childCount) {
                    row.getChildAt(j).alpha = textAlpha
                }
            }
        }

        // 🔥 IMPORTANT: only change background transparency
        root.background?.alpha = backgroundAlpha
    }



}
