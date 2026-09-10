package com.robot.guide.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.robot.guide.data.Vehicle
import com.robot.guide.databinding.ActivityMediaBinding
import com.robot.guide.util.MediaRepository

/**
 * 媒体展示界面 - 车辆图片网格 + 视频列表
 */
class MediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaBinding
    private val repo by lazy { MediaRepository(this) }
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val tabIndex = intent.getIntExtra("tab", 0)
        binding.tvTitle.text = if (tabIndex == 0) "车辆展示" else "车辆视频"

        binding.viewPager.adapter = MediaPagerAdapter(tabIndex)
        binding.viewPager.currentItem = tabIndex

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                binding.viewPager.currentItem = tab.position
                binding.tvTitle.text = if (tab.position == 0) "车辆展示" else "车辆视频"
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
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

    inner class MediaPagerAdapter(private val initialTab: Int) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val rv = RecyclerView(parent.context).apply {
                layoutManager = GridLayoutManager(parent.context, if (viewType == 0) 3 else 1)
                setPadding(16, 16, 16, 16)
            }
            return object : RecyclerView.ViewHolder(rv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val rv = holder.itemView as RecyclerView
            if (position == 0) {
                rv.adapter = VehicleGridAdapter()
                repo.loadVehicles { vehicles ->
                    runOnUiThread {
                        (rv.adapter as VehicleGridAdapter).submitList(vehicles)
                    }
                }
            } else {
                rv.adapter = VideoListAdapter()
                repo.loadVideos { videos ->
                    runOnUiThread {
                        (rv.adapter as VideoListAdapter).submitList(videos)
                    }
                }
            }
        }
    }

    inner class VehicleGridAdapter : RecyclerView.Adapter<VehicleGridAdapter.VH>() {
        private var vehicles: List<Vehicle> = emptyList()

        fun submitList(list: List<Vehicle>) {
            vehicles = list
            notifyDataSetChanged()
        }

        inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
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
            Glide.with(this@MediaActivity)
                .load(v.thumbnail)
                .placeholder(R.drawable.bg_card)
                .centerCrop()
                .into(holder.iv)
        }
    }

    inner class VideoListAdapter : RecyclerView.Adapter<VideoListAdapter.VH>() {
        private var videos: List<com.robot.guide.data.MediaFile> = emptyList()

        fun submitList(list: List<com.robot.guide.data.MediaFile>) {
            videos = list
            notifyDataSetChanged()
        }

        inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
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
                try {
                    if (exoPlayer == null) {
                        exoPlayer = ExoPlayer.Builder(this@MediaActivity).build()
                    }
                    exoPlayer?.setMediaItem(MediaItem.fromUri(v.path))
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    Toast.makeText(this@MediaActivity, "播放: ${v.name}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MediaActivity, "播放失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
