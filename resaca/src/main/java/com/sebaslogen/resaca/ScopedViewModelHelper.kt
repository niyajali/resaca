package com.sebaslogen.resaca

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore


/**
 * Set of functions to help create, store, retrieve and clear a [ViewModel].
 */
@Suppress("NOTHING_TO_INLINE")
object ScopedViewModelHelper {

    /**
     * Returns a [ViewModelProvider.Factory] based on the given ViewModel [builder].
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <T : ViewModel> viewModelFactoryFor(crossinline builder: @DisallowComposableCalls () -> T): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = builder() as VM
        }

    /**
     * Returns a new [ViewModel] of type [T] or creates a new one if none was present in the [scopedObjectsContainer].
     *
     * This function will also compare [positionalMemoizationKey] and [externalKey] to determine
     * if a new instance of [T] needs to be created or an existing one can be returned.
     *
     * The creation of the [ViewModel] will  be done with a [ViewModelProvider] and stored inside
     * a [ViewModelStore] which will be the actual object stored in the [scopedObjectsContainer].
     */
    @Composable
    inline fun <T : ViewModel> getOrBuildViewModel(
        modelClass: Class<T>,
        positionalMemoizationKey: String,
        externalKey: ScopedViewModelContainer.ExternalKey = ScopedViewModelContainer.ExternalKey(),
        factory: ViewModelProvider.Factory,
        scopedObjectsContainer: MutableMap<String, Any>,
        scopedObjectKeys: MutableMap<String, ScopedViewModelContainer.ExternalKey>,
        cancelDisposal: ((String) -> Unit)
    ): T {
        cancelDisposal(positionalMemoizationKey)

        val originalViewModelStore: ViewModelStore? = scopedObjectsContainer[positionalMemoizationKey] as? ViewModelStore

        val viewModel: T =
            if (scopedObjectKeys.containsKey(positionalMemoizationKey)
                && (scopedObjectKeys[positionalMemoizationKey] == externalKey)
                && originalViewModelStore is ViewModelStore
            ) {
                // When the object is already present and the external key matches, then return the existing one in the ViewModelStore
                @Suppress("ReplaceGetOrSet")
                ViewModelProvider(store = originalViewModelStore, factory = factory).get(modelClass)
            } else {
                // Clean-up if needed: the old object is cleared before it's forgotten
                scopedObjectKeys.remove(positionalMemoizationKey)?.also { originalExternalKey ->
                    originalViewModelStore?.let {
                        clearLastDisposedViewModel(originalViewModelStore, originalExternalKey, scopedObjectKeys)
                    }
                }

                // Set the new external key used to track and store the new object version
                scopedObjectKeys[positionalMemoizationKey] = externalKey

                val newViewModelStore = ViewModelStore()
                scopedObjectsContainer[positionalMemoizationKey] = newViewModelStore

                @Suppress("ReplaceGetOrSet")
                ViewModelProvider(store = newViewModelStore, factory = factory).get(modelClass)
            }
        return viewModel
    }

    /**
     * Check if the given [originalExternalKey] is the last one inside [scopedObjectKeys] and if so,
     * clear the [ViewModelStore] and therefore the [ViewModel] inside.
     */
    @PublishedApi
    internal inline fun clearLastDisposedViewModel(
        viewModelStore: ViewModelStore,
        originalExternalKey: ScopedViewModelContainer.ExternalKey,
        scopedObjectKeys: MutableMap<String, ScopedViewModelContainer.ExternalKey>
    ) {
        if (originalExternalKey.isDefaultKey) {
            viewModelStore.clear()
        } else {
            val keyFound = scopedObjectKeys.any { (_, storedExternalKey) -> storedExternalKey == originalExternalKey }
            if (!keyFound) viewModelStore.clear()
        }
    }
}