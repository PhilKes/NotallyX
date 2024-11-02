package com.philkes.notallyx.presentation.view.misc.tristatecheckbox

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.databinding.ChoiceItemTriStateBinding
import com.philkes.notallyx.presentation.dp

fun MaterialAlertDialogBuilder.setMultiChoiceTriStateItems(
    context: Context,
    items: Array<String>,
    checkedStates: Array<TriStateCheckBox.State>,
    onMultiChoiceClickListener: (index: Int, state: TriStateCheckBox.State) -> Unit,
): MaterialAlertDialogBuilder {

    val adapter = TriStateListAdapter(context, items, checkedStates, onMultiChoiceClickListener)
    val recyclerView =
        RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 8.dp(context), 0, 0)
            this.adapter = adapter
        }
    setView(recyclerView)
    return this
}

private class TriStateListAdapter(
    private val context: Context,
    private val items: Array<String>,
    private val checkedStates: Array<TriStateCheckBox.State>,
    private val onItemClick: (index: Int, state: TriStateCheckBox.State) -> Unit,
) : RecyclerView.Adapter<TriStateListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ChoiceItemTriStateBinding.inflate(LayoutInflater.from(context))
        binding.CheckBox.setOnClickListener(null)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, checkedStates[position])
        holder.binding.Layout.setOnClickListener {
            holder.binding.CheckBox.toggleState()
            onItemClick(position, holder.binding.CheckBox.state)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val binding: ChoiceItemTriStateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: String, state: TriStateCheckBox.State) {
            binding.CheckBox.setState(state, true)
            binding.Text.text = item
        }
    }
}
