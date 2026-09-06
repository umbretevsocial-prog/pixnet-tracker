package com.pixnet.tracker.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixnet.tracker.model.*
import java.time.LocalDate

private enum class ReportMode { WEEKLY, CUTOFF }
private data class ReportPeriod(val label:String,val start:LocalDate,val end:LocalDate)
private data class ReportData(
    val period:ReportPeriod,val final:Boolean,
    val pisonet:Double,val printer:Double,val other:Double,
    val expense:Double,val payroll:Double,val contributions:Double,
    val cash:Double,val advance:Double,val unpaidBills:Double,val payrollDue:Double,
    val owners:List<OwnerSummary>,val cutoff:PeriodSummary?
) {
    val collection get()=pisonet+printer+other
    val totalExpense get()=expense+payroll
    val net get()=collection-totalExpense
}

@Composable
fun ReportsScreen(state:PixnetState,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val today=LocalDate.now()
    val effective=minOf(today,PixnetRules.END_DATE)
    var mode by remember { mutableStateOf(ReportMode.WEEKLY) }
    val weeks=remember(effective){ weeklyPeriods(effective) }
    val cutoffs=remember { PixnetRules.PERIODS.map{ReportPeriod(it.name,it.start,it.end)} }
    val currentWeek=weeks.indexOfLast{!effective.isBefore(it.start)}.coerceAtLeast(0)
    val currentCutoff=cutoffs.indexOfLast{!effective.isBefore(it.start)}.coerceAtLeast(0)
    var wi by remember { mutableIntStateOf(currentWeek) }
    var ci by remember { mutableIntStateOf(currentCutoff) }
    val list=if(mode==ReportMode.WEEKLY) weeks else cutoffs
    val index=if(mode==ReportMode.WEEKLY) wi else ci
    val maxIndex=if(mode==ReportMode.WEEKLY) currentWeek else currentCutoff
    val period=list.getOrNull(index)?:return
    val report=remember(state,period,today,mode){ makeReport(state,period,today,mode) }

    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("Business Reports","Weekly and PIXNET cutoff summaries from your live records.") }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(mode==ReportMode.WEEKLY,{mode=ReportMode.WEEKLY;wi=currentWeek},{Text("Weekly")})
                FilterChip(mode==ReportMode.CUTOFF,{mode=ReportMode.CUTOFF;ci=currentCutoff},{Text("Per Cutoff")})
            }
        }
        item {
            Card {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(index>0,{if(mode == ReportMode.WEEKLY) wi-- else ci--}) { Icon(Icons.Default.ChevronLeft,"Previous") }
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(report.period.label,fontWeight=FontWeight.Bold)
                        Text("${formatDate(report.period.start)} - ${formatDate(report.period.end)}",style=MaterialTheme.typography.bodySmall)
                        StatusPill(if(report.final)"FINAL" else "PROJECTED")
                    }
                    IconButton(onClick={if(mode==ReportMode.WEEKLY)wi++ else ci++},enabled=index<maxIndex) { Icon(Icons.Default.ChevronRight,"Next") }
                }
            }
        }
        item {
            Button({
                val intent=Intent(Intent.ACTION_SEND).apply{
                    type="text/plain"
                    putExtra(Intent.EXTRA_SUBJECT,"PIXNET ${report.period.label} Report")
                    putExtra(Intent.EXTRA_TEXT,shareText(report,mode))
                }
                context.startActivity(Intent.createChooser(intent,"Share PIXNET report"))
            },Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share,null); Spacer(Modifier.width(8.dp)); Text("Share Report")
            }
        }

        item { SectionHeader("Collections") }
        item { PairStats("Pisonet",report.pisonet,"Printer",report.printer) }
        item { PairStats("Other",report.other,"Total Collection",report.collection) }

        item { SectionHeader("Expenses & Result") }
        item { PairStats("Expenses",report.expense,"Payroll",report.payroll) }
        item { PairStats("Total Expenses",report.totalExpense,"Net Result",report.net) }

        item { SectionHeader("Business Position","Balances as of this report date.") }
        item { PairStats("PIXNET Cash",report.cash,"Owner Advance",report.advance) }
        item { PairStats("Unpaid Bills",report.unpaidBills,"Payroll Due",report.payrollDue) }
        item { StatCard("Owner Contributions Received",money(report.contributions),Modifier.fillMaxWidth()) }

        if(mode==ReportMode.CUTOFF && report.cutoff!=null) {
            val c=report.cutoff
            item { SectionHeader("Cutoff Settlement") }
            item { PairStats("Advance Repaid",c.ownerAdvanceRepaid,"Contribution Needed",c.ownerContributionNeeded) }
            item { PairStats("Contribution / Owner",c.contributionPerOwner,"Profit / Owner",c.grossProfitSharePerOwner) }
            item { StatCard("Cash Profit Payout",money(c.cashProfitPayoutTotal),Modifier.fillMaxWidth()) }
        }

        item { SectionHeader("Owner Balances","Outstanding owner contribution balances.") }
        items(report.owners,key={it.name}) { owner ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(owner.name,fontWeight=FontWeight.SemiBold)
                        Text(if(owner.outstandingBalance<=0.005)"SETTLED" else "OUTSTANDING",style=MaterialTheme.typography.bodySmall)
                    }
                    Text(money(owner.outstandingBalance),fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun PairStats(a:String,av:Double,b:String,bv:Double) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StatCard(a,money(av),Modifier.weight(1f))
        StatCard(b,money(bv),Modifier.weight(1f),valueColor=if(b=="Net Result"&&bv<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

private fun weeklyPeriods(today:LocalDate):List<ReportPeriod> {
    val out=mutableListOf<ReportPeriod>(); var start=PixnetRules.START_DATE; var n=1
    val limit=minOf(today,PixnetRules.END_DATE)
    while(!start.isAfter(limit)) {
        val end=minOf(start.plusDays(6),PixnetRules.END_DATE)
        out+=ReportPeriod("Week $n",start,end); start=end.plusDays(1); n++
    }
    return out.ifEmpty{listOf(ReportPeriod("Week 1",PixnetRules.START_DATE,PixnetRules.START_DATE.plusDays(6)))}
}

private fun makeReport(state:PixnetState,p:ReportPeriod,today:LocalDate,mode:ReportMode):ReportData {
    val asOf=minOf(today,p.end,PixnetRules.END_DATE)
    val cols=state.collections.filter{!LocalDate.ofEpochDay(it.dateEpochDay).isAfter(asOf)}
    val exps=state.expenses.filter{!LocalDate.ofEpochDay(it.incurredEpochDay).isAfter(asOf)}.map{
        val paid=it.paidEpochDay?.let(LocalDate::ofEpochDay)
        if(paid!=null&&paid.isAfter(asOf))it.copy(paidEpochDay=null) else it
    }
    val att=state.attendance.filter{!LocalDate.ofEpochDay(it.dateEpochDay).isAfter(asOf)}
    val pays=state.payrollPayments.filter{!LocalDate.ofEpochDay(it.dateEpochDay).isAfter(asOf)}
    val contrib=state.ownerContributions.filter{!LocalDate.ofEpochDay(it.dateEpochDay).isAfter(asOf)}
    val at=BusinessCalculator.calculate(cols,exps,att,pays,contrib,asOf)

    val pc=state.collections.filter{LocalDate.ofEpochDay(it.dateEpochDay) in p.start..asOf}
    val pe=state.expenses.filter{LocalDate.ofEpochDay(it.incurredEpochDay) in p.start..asOf}
    val pa=state.attendance.filter{LocalDate.ofEpochDay(it.dateEpochDay) in p.start..asOf}
    val pr=state.ownerContributions.filter{LocalDate.ofEpochDay(it.dateEpochDay) in p.start..asOf}
    val cutoff=if(mode==ReportMode.CUTOFF)at.periods.firstOrNull{it.period.start==p.start&&it.period.end==p.end} else null

    return ReportData(
        p,!today.isBefore(p.end),pc.sumOf{it.pisonet},pc.sumOf{it.printer},pc.sumOf{it.otherIncome},
        pe.sumOf{it.amount},pa.sumOf{PixnetRules.staffSalary(it.staff)},pr.sumOf{it.amount},
        at.currentPixnetCash,at.currentOwnerAdvance,at.unpaidBills,at.outstandingPayroll,at.owners,cutoff
    )
}

private fun shareText(r:ReportData,mode:ReportMode)=buildString {
    appendLine("PIXNET BUSINESS REPORT")
    appendLine(if(mode==ReportMode.WEEKLY)"WEEKLY" else "PER CUTOFF")
    appendLine("${r.period.label}: ${formatDate(r.period.start)} - ${formatDate(r.period.end)}")
    appendLine(if(r.final)"Status: FINAL" else "Status: PROJECTED")
    appendLine(); appendLine("COLLECTIONS")
    appendLine("Pisonet: ${money(r.pisonet)}"); appendLine("Printer: ${money(r.printer)}")
    appendLine("Other: ${money(r.other)}"); appendLine("Total: ${money(r.collection)}")
    appendLine(); appendLine("EXPENSES")
    appendLine("Expenses: ${money(r.expense)}"); appendLine("Payroll: ${money(r.payroll)}")
    appendLine("Total Expenses: ${money(r.totalExpense)}"); appendLine("Net Result: ${money(r.net)}")
    appendLine(); appendLine("BUSINESS POSITION")
    appendLine("PIXNET Cash: ${money(r.cash)}"); appendLine("Owner Advance: ${money(r.advance)}")
    appendLine("Unpaid Bills: ${money(r.unpaidBills)}"); appendLine("Payroll Due: ${money(r.payrollDue)}")
    appendLine("Owner Contributions Received: ${money(r.contributions)}")
    if(mode==ReportMode.CUTOFF&&r.cutoff!=null) {
        val c=r.cutoff; appendLine(); appendLine("CUTOFF SETTLEMENT")
        appendLine("Advance Repaid: ${money(c.ownerAdvanceRepaid)}")
        appendLine("Contribution Needed: ${money(c.ownerContributionNeeded)}")
        appendLine("Contribution / Owner: ${money(c.contributionPerOwner)}")
        appendLine("Profit / Owner: ${money(c.grossProfitSharePerOwner)}")
        appendLine("Cash Profit Payout: ${money(c.cashProfitPayoutTotal)}")
    }
    appendLine(); appendLine("OWNER BALANCES")
    r.owners.forEach{appendLine("${it.name}: ${money(it.outstandingBalance)}")}
}
