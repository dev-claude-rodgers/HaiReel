package com.rodgers.haireel.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.rodgers.haireel.util.themeColor
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReportContainerFragment : Fragment() {

    private var _viewPager: ViewPager2? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurface))
        }

        val tabLayout = TabLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val viewPager = ViewPager2(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0).also { it.weight = 1f }
            isUserInputEnabled = true
        }
        _viewPager = viewPager

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0    -> DailyReportFragment()
                else -> DashboardFragment()
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "日報" else "収支"
        }.attach()

        root.addView(tabLayout)
        root.addView(viewPager)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _viewPager = null
    }

    fun switchToDashboard() { _viewPager?.setCurrentItem(1, true) }
    fun switchToReport()    { _viewPager?.setCurrentItem(0, true) }
}
