package com.gamehac.perappscaler.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamehac.perappscaler.R
import com.gamehac.perappscaler.data.AppItem
import com.gamehac.perappscaler.engine.ResolutionCalculator

class AppAdapter(
    private val onTuneClick: (AppItem) -> Unit
) : ListAdapter<AppItem, AppAdapter.AppViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view, onTuneClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        itemView: View,
        private val onTuneClick: (AppItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvPkg: TextView = itemView.findViewById(R.id.tvPackageName)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvBadgeCategory)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvBadgeStatus)
        private val btnTune: ImageButton = itemView.findViewById(R.id.btnTune)

        fun bind(item: AppItem) {
            val context = itemView.context
            ivIcon.setImageDrawable(item.icon)
            tvName.text = item.label
            tvPkg.text = item.packageName

            // Category badge
            if (item.isGame) {
                tvCategory.setText(R.string.category_game)
                tvCategory.setBackgroundResource(R.drawable.bg_badge_game)
            } else {
                tvCategory.setText(R.string.category_app)
                tvCategory.setBackgroundResource(R.drawable.bg_badge_native)
            }

            // Status badge
            if (item.isScaled) {
                val nativeSize = ResolutionCalculator.getNativeDisplaySize(context)
                val estimatedStr = ResolutionCalculator.formatEstimatedResolution(
                    nativeSize.width,
                    nativeSize.height,
                    item.getScaleFactor()
                )
                val fpsStr = if (item.fps > 0) "${item.fps} FPS" else context.getString(R.string.fps_uncapped)
                tvStatus.text = context.getString(R.string.status_scaled, item.scalePercent, estimatedStr, fpsStr)
                tvStatus.setBackgroundResource(R.drawable.bg_badge_scaled)
                tvStatus.visibility = View.VISIBLE
            } else {
                tvStatus.setText(R.string.status_native)
                tvStatus.setBackgroundResource(R.drawable.bg_badge_native)
                tvStatus.visibility = View.VISIBLE
            }

            itemView.setOnClickListener { onTuneClick(item) }
            btnTune.setOnClickListener { onTuneClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppItem>() {
            override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
                return oldItem.scalePercent == newItem.scalePercent &&
                        oldItem.fps == newItem.fps &&
                        oldItem.label == newItem.label
            }
        }
    }
}
