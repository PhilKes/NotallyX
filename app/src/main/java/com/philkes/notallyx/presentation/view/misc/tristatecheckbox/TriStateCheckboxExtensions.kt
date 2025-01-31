package com.philkes.notallyx.presentation.view.misc.tristatecheckbox

import android.content.Context
import android.view.LayoutInflater
import android.view.View.OnClickListener
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
            setPadding(0, 8.dp, 0, 0)
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
        return ViewHolder(ChoiceItemTriStateBinding.inflate(LayoutInflater.from(context)))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, checkedStates[position])
        val onClickListener = OnClickListener {
            holder.binding.CheckBox.toggleState()
            onItemClick(holder.bindingAdapterPosition, holder.binding.CheckBox.state)
            notifyItemChanged(holder.bindingAdapterPosition)
        }
        holder.binding.apply {
            Layout.setOnClickListener(onClickListener)
            CheckBox.setOnClickListener(onClickListener)
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
