package com.robot.guide.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.robot.guide.data.MediaFile
import com.robot.guide.databinding.ActivityMediaBinding
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import androidx.viewpager2.widget.ViewPager2

/**
 * 媒体展示界面 - 图片浏览 + 视频播放
 */
class MediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaBinding
    private var exoPlayer: ExoPlayer? = null
    private var currentMediaList: List<MediaFile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // 检查存储权限
        checkAndRequestPermission()

        // 初始加载
        loadMediaFiles()

        // ViewPager2设置
        val tabIndex = intent.getIntExtra("tab", 0)
        binding.viewPager.adapter = MediaPagerAdapter()
        binding.viewPager.currentItem = tabIndex

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                binding.viewPager.currentItem = tab.position
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position))
            }
        })
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 300)
            }
        }
    }

    private fun loadMediaFiles() {
        // 简化版本 - 从assets或外部存储加载
        // 实际部署时应该扫描指定目录（如 /sdcard/RobotGuide/media/）
    }

    private fun initializeExoPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build()
            // 在实际使用中，这里需要为视频tab的播放器设置ExoPlayer
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }

    /**
     * ViewPager2的Adapter - 图片网格 + 视频列表
     */
    inner class MediaPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = 2 // 图片tab + 视频tab

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val rv = RecyclerView(parent.context).apply {
                layoutManager = GridLayoutManager(parent.context, 3)
            }
            return object : RecyclerView.ViewHolder(rv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val recyclerView = holder.itemView as RecyclerView
            if (position == 0) {
                // 图片tab
                recyclerView.adapter = ImageGridAdapter()
            } else {
                // 视频tab
                recyclerView.adapter = VideoListAdapter()
            }
        }
    }

    inner class ImageGridAdapter : RecyclerView.Adapter<ImageGridAdapter.ViewHolder>() {
        private val sampleImages = listOf(
            "https://picsum.photos/400/300?random=1",
            "https://picsum.photos/400/300?random=2",
            "https://picsum.photos/400/300?random=3",
            "https://picsum.photos/400/300?random=4"
        )

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(android.R.id.content) as? ImageView
                ?: ImageView(itemView.context)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = sampleImages[position % sampleImages.size]
            Glide.with(this@MediaActivity)
                .load(url)
                .into(holder.imageView)

            holder.itemView.setOnClickListener {
                // 全屏查看图片
                openFullscreenImage(url)
            }
        }

        override fun getItemCount() = sampleImages.size + 10 // 模拟更多
    }

    inner class VideoListAdapter : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {
        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemView.setOnClickListener {
                // 播放视频
            }
        }

        override fun getItemCount() = 0 // 实际部署时从存储扫描
    }

    private fun openFullscreenImage(url: String) {
        // 简化版本 - Toast 提示
        android.widget.Toast.makeText(this, "图片: $url", android.widget.Toast.LENGTH_SHORT).show()
    }
}
