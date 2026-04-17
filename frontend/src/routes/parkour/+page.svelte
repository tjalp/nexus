<script lang="ts">
	import type { PageData } from './$types';
	import { cn } from '$lib/utils';

	export let data: PageData;

	const statusBadges: Record<string, string> = {
		verified: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-200',
		pending: 'bg-amber-500/15 text-amber-700 dark:text-amber-100',
		rejected: 'bg-red-500/15 text-red-600 dark:text-red-200'
	};

	function formatMs(value: number) {
		return (value / 1000).toFixed(2) + 's';
	}
	function formatDate(value: string) {
		return new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
	}
</script>

<svelte:head>
	<title>Parkour Stats | Nexus Control</title>
	<meta name="description" content="Global parkour records and run submissions" />
</svelte:head>

<section class="space-y-4">
	<div class="flex flex-wrap items-center justify-between gap-3">
		<div>
			<p class="heading-label">Parkour statistics</p>
			<h1 class="text-2xl font-semibold">Global records &amp; submissions</h1>
			<p class="text-sm text-muted-foreground">Data is fetched from the backend endpoints with safe fallbacks.</p>
		</div>
		<div class="rounded-lg border border-muted-foreground/20 bg-white/80 px-3 py-2 text-xs text-muted-foreground shadow-inner dark:bg-white/5">
			API: <span class="mono text-foreground">/api/parkour/records</span> & <span class="mono text-foreground">/api/parkour/runs</span>
		</div>
	</div>

	<div class="grid gap-4 lg:grid-cols-2">
		<div class="panel space-y-3">
			<p class="heading-label">Global records</p>
			<div class="overflow-hidden rounded-lg border border-muted-foreground/20">
				<table class="min-w-full divide-y divide-muted-foreground/10 text-sm">
					<thead class="bg-muted/40 text-muted-foreground">
						<tr>
							<th scope="col" class="px-3 py-2 text-left font-semibold">Course</th>
							<th scope="col" class="px-3 py-2 text-left font-semibold">Player</th>
							<th scope="col" class="px-3 py-2 text-left font-semibold">Time</th>
							<th scope="col" class="px-3 py-2 text-left font-semibold">Date</th>
						</tr>
					</thead>
					<tbody class="divide-y divide-muted-foreground/10 bg-white/70 dark:bg-white/5">
						{#each data.records as record}
							<tr class="hover:bg-primary/5 focus-within:bg-primary/5">
								<td class="px-3 py-2 font-semibold text-foreground">{record.course}</td>
								<td class="px-3 py-2 text-muted-foreground">{record.player}</td>
								<td class="px-3 py-2 text-foreground">{formatMs(record.timeMs)}</td>
								<td class="px-3 py-2 text-muted-foreground">{formatDate(record.date)}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</div>

		<div class="panel space-y-3">
			<p class="heading-label">Legend</p>
			<ul class="space-y-2 text-sm text-muted-foreground">
				<li>Records and runs are provided by backend endpoints with sample data until persistence is added.</li>
				<li>Times are shown in seconds (two decimals). Rows highlight on focus for accessibility.</li>
				<li>Use the command palette to jump here quickly (Ctrl/Cmd + K).</li>
			</ul>
		</div>
	</div>

	<div class="panel space-y-3">
		<div class="flex items-center justify-between">
			<p class="heading-label">All run submissions</p>
			<p class="text-xs text-muted-foreground">{data.runs.length} runs</p>
		</div>
		<div class="overflow-auto rounded-xl border border-muted-foreground/20">
			<table class="min-w-full divide-y divide-muted-foreground/10 text-sm" role="table">
				<thead class="sticky top-0 bg-muted/60 text-muted-foreground backdrop-blur">
					<tr>
						<th scope="col" class="px-3 py-2 text-left font-semibold">Run ID</th>
						<th scope="col" class="px-3 py-2 text-left font-semibold">Player</th>
						<th scope="col" class="px-3 py-2 text-left font-semibold">Course</th>
						<th scope="col" class="px-3 py-2 text-left font-semibold">Time</th>
						<th scope="col" class="px-3 py-2 text-left font-semibold">Status</th>
						<th scope="col" class="px-3 py-2 text-left font-semibold">Submitted</th>
					</tr>
				</thead>
				<tbody class="divide-y divide-muted-foreground/10 bg-white/70 dark:bg-white/5">
					{#each data.runs as run}
						<tr class="hover:bg-primary/5 focus-within:bg-primary/5">
							<td class="px-3 py-2 mono text-foreground">{run.id}</td>
							<td class="px-3 py-2 text-muted-foreground">{run.player}</td>
							<td class="px-3 py-2 text-foreground">{run.course}</td>
							<td class="px-3 py-2 text-foreground">{formatMs(run.timeMs)}</td>
							<td class="px-3 py-2">
								<span class={cn('rounded-full px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.15em]', statusBadges[run.status])}>
									{run.status}
								</span>
							</td>
							<td class="px-3 py-2 text-muted-foreground">{formatDate(run.submittedAt)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	</div>
</section>
