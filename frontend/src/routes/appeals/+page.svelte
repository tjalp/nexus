<script lang="ts">
	import { Button as BitsButton, Tabs } from 'bits-ui';
	import { cn, normalizeUsername } from '$lib/utils';
	import type { PageData } from './$types';

	export let data: PageData;
	let tab: 'all' | 'open' | 'review' | 'closed' = 'open';
	let summary = '';
	let username = data.profiles[2]?.username ?? 'rook';

	$: filteredAppeals = data.appeals.filter((appeal) =>
		tab === 'all' ? true : appeal.status === tab
	);
</script>

<svelte:head>
	<title>Nexus Control | Appeals</title>
	<meta name="description" content="Appeal intake and review workspace" />
</svelte:head>

<div class="space-y-8">
	<header class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
		<div>
			<p class="heading-label">Appeals</p>
			<h1 class="text-2xl font-semibold">Intake &amp; review workspace</h1>
			<p class="text-sm text-muted-foreground">
				Everyone can submit an appeal. Moderators with the right role can triage and finalize.
			</p>
		</div>
		<BitsButton.Root
			href="/login"
			class="rounded-lg border border-muted-foreground/40 bg-white/70 px-3 py-2 text-sm font-semibold shadow hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5"
		>
			Sign in to review
		</BitsButton.Root>
	</header>

	<section class="grid gap-6 lg:grid-cols-3">
		<div class="panel space-y-4 lg:col-span-2">
			<div class="flex items-center justify-between">
				<div>
					<p class="heading-label">Inbox</p>
					<h3 class="text-lg font-semibold">Appeals by status</h3>
				</div>
				<Tabs.Root
					value={tab}
					onValueChange={(value) => (tab = value as typeof tab)}
					class="flex gap-2"
				>
					<Tabs.List class="flex gap-2">
						{#each ['all', 'open', 'review', 'closed'] as value}
							<Tabs.Trigger
								{value}
								class="rounded-md border border-muted-foreground/30 px-3 py-1 text-xs font-semibold tracking-[0.18em] uppercase data-[state=active]:border-primary/60 data-[state=active]:bg-primary/10"
							>
								{value}
							</Tabs.Trigger>
						{/each}
					</Tabs.List>
				</Tabs.Root>
			</div>
			<div class="space-y-3">
				{#each filteredAppeals as appeal}
					<div
						class="rounded-xl border border-muted-foreground/30 bg-white/70 p-4 shadow-inner dark:bg-white/5"
					>
						<div class="flex items-center justify-between gap-3">
							<div>
								<p class="mono text-xs text-muted-foreground">{appeal.id}</p>
								<p class="text-base font-semibold">{appeal.displayName}</p>
								<p class="text-sm text-muted-foreground">
									@{appeal.username} · {appeal.type} · {appeal.channel}
								</p>
							</div>
							<span
								class={cn(
									'rounded-md px-2 py-1 text-[11px] font-semibold tracking-[0.18em] uppercase',
									appeal.status === 'closed' &&
										'bg-emerald-500/15 text-emerald-700 dark:text-emerald-200',
									appeal.status === 'review' &&
										'bg-amber-500/15 text-amber-700 dark:text-amber-100',
									appeal.status === 'open' && 'bg-blue-500/15 text-blue-700 dark:text-blue-100'
								)}
							>
								{appeal.status}
							</span>
						</div>
						<p class="mt-2 text-sm leading-relaxed text-foreground">{appeal.summary}</p>
						<p class="text-xs text-muted-foreground">
							Reference: {appeal.reference} · Submitted at {appeal.submittedAt}
						</p>
					</div>
				{/each}
			</div>
		</div>

		<div class="panel space-y-3">
			<p class="heading-label">Submit appeal</p>
			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Username (lowercase only)</span>
				<input
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					value={username}
					oninput={(event) => (username = normalizeUsername(event.currentTarget.value))}
				/>
			</label>
			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Summary</span>
				<textarea
					class="h-24 w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					value={summary}
					oninput={(event) => (summary = event.currentTarget.value)}
					placeholder="Describe what happened and why you're appealing."
				></textarea>
			</label>
			<BitsButton.Root
				class="rounded-lg border border-primary/50 bg-primary/15 px-3 py-2 text-sm font-semibold text-primary-foreground shadow hover:border-primary/60 hover:bg-primary/25"
			>
				Submit (backend wiring pending)
			</BitsButton.Root>
			<p class="text-xs text-muted-foreground">
				Appeals stay visible even if you do not have server management rights.
			</p>
		</div>
	</section>
</div>
