package com.homeostasis.app.ui.profile

import android.app.Activity
import android.content.Context // Import Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.homeostasis.app.databinding.FragmentProfileSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext // Import ApplicationContext
import java.io.File // Import File
import javax.inject.Inject

@AndroidEntryPoint
class ProfileSettingsFragment : Fragment() {

    private var _binding: FragmentProfileSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileSettingsViewModel by viewModels()

    @Inject
    @ApplicationContext
    lateinit var appContext: Context // Inject ApplicationContext

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Pass the selected image URI to the ViewModel for processing
            viewModel.handleImageSelection(requireContext(), it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProfileSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe user profile data
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let { user -> // Renamed to 'user' for clarity
                binding.editTextName.setText(user.name)

                // Derive the local profile picture file path
                val localFilePath = File(appContext.filesDir, "profile_picture_${user.id}.jpg").absolutePath
                val localFile = File(localFilePath)

                if (localFile.exists()) {
                    // Load from local file if it exists
                    Glide.with(this)
                        .load(localFile)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(com.homeostasis.app.R.drawable.ic_default_profile) // Use a default image
                        .into(binding.imageViewProfilePicture)
                } else if (user.profileImageUrl.isNotEmpty()) {
                    // Load from remote URL if local file doesn't exist and remote URL is available
                    Glide.with(this)
                        .load(user.profileImageUrl)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(com.homeostasis.app.R.drawable.ic_default_profile) // Use a default image
                        .into(binding.imageViewProfilePicture)
                } else {
                    // Load default image if no local file and no remote URL
                    binding.imageViewProfilePicture.setImageResource(com.homeostasis.app.R.drawable.ic_default_profile)
                }
            }
        }

        // Observe selected image preview data
        viewModel.selectedImagePreview.observe(viewLifecycleOwner) { imageBytes ->
            if (imageBytes != null) {
                // Load selected image preview using Glide with circular transformation
                Glide.with(this)
                    .load(imageBytes) // Load from ByteArray
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.imageViewProfilePicture)
            } else {
                // Load default image if selected image is null (e.g., processing failed)
                binding.imageViewProfilePicture.setImageResource(com.homeostasis.app.R.drawable.ic_default_profile)
            }
        }

        // Set up click listener for selecting profile picture
        binding.buttonSelectPicture.setOnClickListener {
            selectImageLauncher.launch("image/*") // Launch gallery to select an image
        }

        // Set up click listener for saving profile
        binding.buttonSaveProfile.setOnClickListener {
            val name = binding.editTextName.text.toString()
            viewModel.saveProfile(name)
            // TODO: Show a confirmation message or navigate back
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}