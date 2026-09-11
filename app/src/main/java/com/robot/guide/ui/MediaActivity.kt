package com.robot.guide.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.robot.guide.R
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.robot.guide.data.Vehicle
import com.robot.guide.databinding.ActivityMediaBinding
import com.robot.guide.util.MediaRepository

/**
 * 媒体展示界面 - 车辆图片网格 + 视频列表
 *
 * 修复点：
 *   1. 用 RecyclerView + Fragment 切换代替 ViewPager2（更稳定）
 *   2. 视频用 VideoView（系统组件，不依赖 ExoPlayer）
 *   3. 图片用 Glide 加载本地 drawable
 */
class MediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaBinding
    private val repo by lazy { MediaRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val tabIndex = intent.getIntExtra("tab", 0)
        binding.tvTitle.text = if (tabIndex == 0) "车辆展示" else "车辆视频"

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                binding.tvTitle.text = if (tab.position == 0) "车辆展示" else "车辆视频"
                showTab(tab.position)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        // 设置默认 Tab
        binding.tabLayout.getTabAt(tabIndex)?.select()
        showTab(tabIndex)
    }

    private fun showTab(index: Int) {
        if (index == 0) {
            showVehicleGrid()
        } else {
            showVideoList()
        }
    }

    // ========== 车辆展示 ==========

    private fun showVehicleGrid() {
        binding.viewPager.visibility = View.GONE
        binding.rvContent.visibility = View.VISIBLE
        binding.rvContent.layoutManager = GridLayoutManager(this, 3)
        binding.rvContent.adapter = VehicleGridAdapter()

        repo.loadVehicles { vehicles ->
            runOnUiThread {
                (binding.rvContent.adapter as VehicleGridAdapter).submitList(vehicles)
            }
        }
    }

    inner class VehicleGridAdapter : RecyclerView.Adapter<VehicleGridAdapter.VH>() {
        private var vehicles: List<Vehicle> = emptyList()

        fun submitList(list: List<Vehicle>) {
            vehicles = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view.findViewById(R.id.ivVehicle)
            val tv: TextView = view.findViewById(R.id.tvVehicleName)
        }

        override fun getItemCount() = vehicles.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_vehicle_card, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val v = vehicles[position]
            holder.tv.text = v.name
            try {
                Glide.with(this@MediaActivity)
                    .load(v.thumbnail)
                    .placeholder(R.drawable.placeholder_car1)
                    .centerCrop()
                    .into(holder.iv)
            } catch (e: Exception) {
                holder.iv.setImageResource(R.drawable.placeholder_car1)
            }
        }
    }

    // ========== 视频列表 ==========

    private fun showVideoList() {
        binding.viewPager.visibility = View.GONE
        binding.rvContent.visibility = View.VISIBLE
        binding.rvContent.layoutManager = LinearLayoutManager(this)
        binding.rvContent.adapter = VideoListAdapter()

        repo.loadVideos { videos ->
            runOnUiThread {
                if (videos.isEmpty()) {
                    Toast.makeText(this, "暂无视频，请在后台配置视频资源", Toast.LENGTH_LONG).show()
                }
                (binding.rvContent.adapter as VideoListAdapter).submitList(videos)
            }
        }
    }

    inner class VideoListAdapter : RecyclerView.Adapter<VideoListAdapter.VH>() {
        private var videos: List<com.robot.guide.data.MediaFile> = emptyList()

        fun submitList(list: List<com.robot.guide.data.MediaFile>) {
            videos = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tv: TextView = view.findViewById(R.id.tvVideoName)
        }

        override fun getItemCount() = videos.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video_row, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val v = videos[position]
            holder.tv.text = v.name
            holder.itemView.setOnClickListener {
                playVideo(v.path, v.name)
            }
        }
    }

    private fun playVideo(url: String, name: String) {
        try {
            val videoView = binding.videoView
            binding.videoContainer.visibility = View.VISIBLE
            binding.rvContent.visibility = View.GONE
            binding.tabLayout.visibility = View.GONE

            videoView.setVideoPath(url)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = false
                videoView.start()
            }
            videoView.setOnCompletionListener {
                Toast.makeText(this, "播放完成: $name", Toast.LENGTH_SHORT).show()
            }
            videoView.setOnErrorListener { _, what, extra ->
                Toast.makeText(this, "视频播放失败 (错误码: $what/$extra)", Toast.LENGTH_SHORT).show()
                binding.videoContainer.visibility = View.GONE
                binding.rvContent.visibility = View.VISIBLE
                binding.tabLayout.visibility = View.VISIBLE
                true
            }

            // 返回按钮
            binding.btnVideoBack.setOnClickListener {
                videoView.stopPlayback()
                binding.videoContainer.visibility = View.GONE
                binding.rvContent.visibility = View.VISIBLE
                binding.tabLayout.visibility = View.VISIBLE
            }

            Toast.makeText(this, "正在播放: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
