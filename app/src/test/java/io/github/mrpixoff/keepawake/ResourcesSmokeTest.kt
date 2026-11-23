package io.github.mrpixoff.keepawake

import io.github.mrpixoff.keepawake.databinding.ActivityMainBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResourcesSmokeTest {

    @Test
    fun app_name_and_tile_icons_are_resolvable() {
        val app = RuntimeEnvironment.getApplication()

        assertEquals("KeepAwake", app.getString(R.string.app_name))
        assertNotNull(app.resources.getDrawable(R.drawable.ic_tile_active, null))
        assertNotNull(app.resources.getDrawable(R.drawable.ic_tile_inactive, null))
        assertNotNull(app.resources.getDrawable(R.drawable.ic_tile_error, null))
        assertNotNull(app.resources.getDrawable(R.drawable.ic_notification_small, null))
    }

    @Test
    fun main_layout_inflates_with_view_binding() {
        val app = RuntimeEnvironment.getApplication()
        val binding = ActivityMainBinding.inflate(android.view.LayoutInflater.from(app))

        assertNotNull(binding.root)
        assertNotNull(binding.toggleButton)
        assertNotNull(binding.statusText)
    }
}
