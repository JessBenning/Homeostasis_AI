package com.homeostasis.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.homeostasis.app.databinding.FragmentHouseholdGroupBinding
import com.homeostasis.app.viewmodel.HouseholdGroupViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HouseholdGroupFragment : Fragment() {

    private var _binding: FragmentHouseholdGroupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HouseholdGroupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHouseholdGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

