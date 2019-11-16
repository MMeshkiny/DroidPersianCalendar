package com.byagowi.persiancalendar.ui.calendar.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.byagowi.persiancalendar.PREF_SHIFT_WORK_RECURS
import com.byagowi.persiancalendar.PREF_SHIFT_WORK_SETTING
import com.byagowi.persiancalendar.PREF_SHIFT_WORK_STARTING_JDN
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.ShiftWorkItemBinding
import com.byagowi.persiancalendar.databinding.ShiftWorkSettingsBinding
import com.byagowi.persiancalendar.di.AppDependency
import com.byagowi.persiancalendar.di.CalendarFragmentDependency
import com.byagowi.persiancalendar.di.MainActivityDependency
import com.byagowi.persiancalendar.entities.ShiftWorkRecord
import com.byagowi.persiancalendar.utils.*
import dagger.android.support.DaggerAppCompatDialogFragment
import javax.inject.Inject

class ShiftWorkDialog : DaggerAppCompatDialogFragment() {

    @Inject
    lateinit var appDependency: AppDependency
    @Inject
    lateinit var mainActivityDependency: MainActivityDependency
    @Inject
    lateinit var calendarFragmentDependency: CalendarFragmentDependency

    private var jdn: Long = -1L
    private var selectedJdn: Long = -1L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mainActivity = mainActivityDependency.mainActivity

        applyAppLanguage(mainActivity)
        updateStoredPreference(mainActivity)

        selectedJdn = arguments?.getLong(BUNDLE_KEY, -1L) ?: -1L
        if (selectedJdn == -1L) selectedJdn = getTodayJdn()

        jdn = shiftWorkStartingJdn
        var isFirstSetup = false
        if (jdn == -1L) {
            isFirstSetup = true
            jdn = selectedJdn
        }

        val binding = ShiftWorkSettingsBinding.inflate(
            LayoutInflater.from(mainActivity), null, false
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(mainActivity)
        val shiftWorkItemAdapter = ItemsAdapter(
            if (shiftWorks.isEmpty()) listOf(ShiftWorkRecord("d", 0)) else shiftWorks,
            binding
        )
        binding.recyclerView.adapter = shiftWorkItemAdapter

        binding.description.text = String.format(
            getString(
                if (isFirstSetup) R.string.shift_work_starting_date else R.string.shift_work_starting_date_edit
            ),
            formatDate(
                getDateFromJdnOfCalendar(mainCalendar, jdn)
            )
        )

        binding.resetLink.setOnClickListener {
            jdn = selectedJdn
            binding.description.text = String.format(
                getString(R.string.shift_work_starting_date),
                formatDate(
                    getDateFromJdnOfCalendar(mainCalendar, jdn)
                )
            )
            shiftWorkItemAdapter.reset()
        }
        binding.recurs.isChecked = shiftWorkRecurs

        return AlertDialog.Builder(mainActivity)
            .setView(binding.root)
            .setTitle(null)
            .setPositiveButton(R.string.accept) { _, _ ->
                val result = shiftWorkItemAdapter.rows.filter { it.length != 0 }.joinToString(",") {
                    "${it.type.replace("=", "").replace(",", "")}=${it.length}"
                }

                appDependency.sharedPreferences.edit {
                    putLong(PREF_SHIFT_WORK_STARTING_JDN, if (result.isEmpty()) -1 else jdn)
                    putString(PREF_SHIFT_WORK_SETTING, result.toString())
                    putBoolean(PREF_SHIFT_WORK_RECURS, binding.recurs.isChecked)
                }

                calendarFragmentDependency.calendarFragment.afterShiftWorkChange()
                mainActivity.restartActivity()
            }
            .setCancelable(true)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onResume() {
        super.onResume()

        // https://stackoverflow.com/a/46248107
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }


    private inner class ItemsAdapter internal constructor(
        initialItems: List<ShiftWorkRecord>,
        private val mBinding: ShiftWorkSettingsBinding
    ) : RecyclerView.Adapter<ItemsAdapter.ViewHolder>() {
        private val mRows = ArrayList<ShiftWorkRecord>()

        internal val rows: List<ShiftWorkRecord>
            get() = mRows

        init {
            mRows.addAll(initialItems)
            updateShiftWorkResult()
        }

        fun shiftWorkKeyToString(type: String): String = shiftWorkTitles[type] ?: type

        private fun updateShiftWorkResult() =
            mRows.filter { it.length != 0 }.joinToString(spacedComma) {
                String.format(
                    getString(R.string.shift_work_record_title),
                    formatNumber(it.length), shiftWorkKeyToString(it.type)
                )
            }.also {
                mBinding.result.text = it
                mBinding.result.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ShiftWorkItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)

        override fun getItemCount(): Int = mRows.size + 1

        internal fun reset() {
            mRows.clear()
            mRows.add(ShiftWorkRecord("d", 0))
            notifyDataSetChanged()
            updateShiftWorkResult()
        }

        internal inner class ViewHolder(private val mBinding: ShiftWorkItemBinding) :
            RecyclerView.ViewHolder(mBinding.root) {
            private var mPosition: Int = 0

            init {
                val context = mBinding.root.context

                mBinding.lengthSpinner.adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    (0..7).map {
                        if (it == 0) getString(R.string.shift_work_days_head) else formatNumber(it)
                    }
                )

                mBinding.typeAutoCompleteTextView.run {
                    val adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        resources.getStringArray(R.array.shift_work)
                    )
                    setAdapter(adapter)
                    setOnClickListener {
                        if (text.toString().isNotEmpty()) adapter.filter.filter(null)
                        showDropDown()
                    }
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>, view: View, position: Int, id: Long
                        ) {
                            mRows[mPosition] =
                                ShiftWorkRecord(text.toString(), mRows[mPosition].length)
                            updateShiftWorkResult()
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }
                    addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {}

                        override fun beforeTextChanged(
                            s: CharSequence?, start: Int, count: Int, after: Int
                        ) = Unit

                        override fun onTextChanged(
                            s: CharSequence?, start: Int, before: Int, count: Int
                        ) {
                            mRows[mPosition] =
                                ShiftWorkRecord(text.toString(), mRows[mPosition].length)
                            updateShiftWorkResult()
                        }
                    })
                    filters = arrayOf(object : InputFilter {
                        override fun filter(
                            source: CharSequence?, start: Int, end: Int,
                            dest: Spanned?, dstart: Int, dend: Int
                        ): CharSequence? =
                            if (source?.contains("[=,]".toRegex()) == true) "" else null
                    })
                }

                mBinding.remove.setOnClickListener { remove() }

                mBinding.lengthSpinner.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                        override fun onItemSelected(
                            parent: AdapterView<*>, view: View, position: Int, id: Long
                        ) {
                            mRows[mPosition] = ShiftWorkRecord(mRows[mPosition].type, position)
                            updateShiftWorkResult()
                        }
                    }

                mBinding.addButton.setOnClickListener {
                    mRows.add(ShiftWorkRecord("r", 0))
                    notifyDataSetChanged()
                    updateShiftWorkResult()
                }
            }

            fun remove() {
                mRows.removeAt(mPosition)
                notifyDataSetChanged()
                updateShiftWorkResult()
            }

            fun bind(position: Int) = if (position < mRows.size) {
                val shiftWorkRecord = mRows[position]
                mPosition = position
                mBinding.rowNumber.text = String.format("%s:", formatNumber(position + 1))
                mBinding.lengthSpinner.setSelection(shiftWorkRecord.length)
                mBinding.typeAutoCompleteTextView.setText(shiftWorkKeyToString(shiftWorkRecord.type))
                mBinding.detail.visibility = View.VISIBLE
                mBinding.addButton.visibility = View.GONE
            } else {
                mBinding.detail.visibility = View.GONE
                mBinding.addButton.visibility = if (mRows.size < 20) View.VISIBLE else View.GONE
            }
        }
    }

    companion object {
        private const val BUNDLE_KEY = "jdn"

        fun newInstance(jdn: Long) = ShiftWorkDialog().apply {
            arguments = Bundle().apply {
                putLong(BUNDLE_KEY, jdn)
            }
        }
    }
}
