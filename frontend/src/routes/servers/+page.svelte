<script lang="ts">
	import { Button as BitsButton, Separator, Tabs } from 'bits-ui';
	import { canManageServers, isAuthenticated, userRole } from '$lib/auth';
	import { cn } from '$lib/utils';
	import type { PageData } from './$types';

	export let data: PageData;

	let statusFilter: 'all' | 'online' | 'degraded' | 'offline' = 'all';
	let regionFilter: 'all' | 'EU-West' | 'EU-North' | 'US-East' = 'all';

	$: allowed = $canManageServers;
	$: role = $userRole ?? 'viewer';
	$: filteredServers = data.servers.filter((server) => {
		const matchesStatus = statusFilter === 'all' || server.status === statusFilter;
		const matchesRegion = regionFilter === 'all' || server.region === regionFilter;
		return matchesStatus && matchesRegion;
	});

	$: totalOnline = filteredServers.reduce(
		(acc, server) => {
			return {
				players: acc.players + server.playerCount,
				max: acc.max + server.maxPlayers
			};
		},
		{ players: 0, max: 0 }
	);
</script>

<svelte:head>
	<title>Nexus Control | Servers</title>
	<meta name="description" content="Server management dashboard" />
</svelte:head>

<div class="space-y-8">
	<header class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
		<div>
			<p class="heading-label">Server Management</p>
			<h1 class="text-2xl font-semibold">Cluster health and routing</h1>
			<p class="text-sm text-muted-foreground">
				Only privileged users can interact with server controls. Others can still see live
				telemetry.
			</p>
		</div>
		<div class="flex items-center gap-2 text-xs text-muted-foreground">
			<span class="rounded-md border border-muted-foreground/30 bg-muted/15 px-2 py-1"
				>Active role: {role}</span
			>
			<span class="rounded-md border border-muted-foreground/30 bg-muted/15 px-2 py-1"
				>Privileged roles: {data.privilegedRoles.join(', ')}</span
			>
		</div>
	</header>

	{#if !$isAuthenticated}
		<div
			class="panel border-dashed border-primary/40 bg-primary/10 text-sm text-primary-foreground"
		>
			Sign in to request access to server controls. Without authentication you can only view public
			telemetry.
		</div>
	{:else if !allowed}
		<div
			class="panel border-dashed border-amber-400/60 bg-amber-500/10 text-sm text-amber-900 dark:text-amber-50"
		>
			Your role does not allow server management yet. Ask an operator to add you to one of the
			privileged roles to unlock console streaming and commands.
		</div>
	{/if}

	<section class="grid gap-4 md:grid-cols-3">
		<div class="panel">
			<p class="heading-label">Players</p>
			<p class="mt-2 text-3xl font-semibold">{totalOnline.players}</p>
			<p class="text-xs text-muted-foreground">
				Across filtered servers ({totalOnline.max} capacity)
			</p>
		</div>
		<div class="panel">
			<p class="heading-label">Filters</p>
			<div class="mt-3 flex flex-wrap gap-2">
				<Tabs.Root
					value={statusFilter}
					onValueChange={(value) => (statusFilter = value as typeof statusFilter)}
					class="flex flex-wrap gap-2"
				>
					<Tabs.List class="flex flex-wrap gap-2">
						{#each ['all', 'online', 'degraded', 'offline'] as status}
							<Tabs.Trigger
								value={status}
								class="rounded-md border border-muted-foreground/30 px-3 py-1 text-xs font-semibold tracking-[0.18em] uppercase data-[state=active]:border-primary/60 data-[state=active]:bg-primary/10"
							>
								{status}
							</Tabs.Trigger>
						{/each}
					</Tabs.List>
				</Tabs.Root>
			</div>
			<div class="mt-3 flex flex-wrap gap-2 text-xs">
				<button
					class={cn(
						'rounded-md border px-3 py-1 font-semibold transition',
						regionFilter === 'all'
							? 'border-primary/60 bg-primary/10 text-primary-foreground'
							: 'border-muted-foreground/30 bg-white/70 text-foreground hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5'
					)}
					onclick={() => (regionFilter = 'all')}
				>
					All regions
				</button>
				{#each ['EU-West', 'EU-North', 'US-East'] as region}
					<button
						class={cn(
							'rounded-md border px-3 py-1 font-semibold transition',
							regionFilter === region
								? 'border-primary/60 bg-primary/10 text-primary-foreground'
								: 'border-muted-foreground/30 bg-white/70 text-foreground hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5'
						)}
						onclick={() => (regionFilter = region as typeof regionFilter)}
					>
						{region}
					</button>
				{/each}
			</div>
		</div>
		<div class="panel">
			<p class="heading-label">Actions</p>
			<div class="mt-3 flex flex-wrap gap-2 text-sm">
				<BitsButton.Root
					href="/"
					class="rounded-lg border border-muted-foreground/40 bg-white/70 px-3 py-2 font-semibold shadow hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5"
				>
					Back to overview
				</BitsButton.Root>
				{#if allowed}
					<BitsButton.Root
						class="rounded-lg border border-primary/50 bg-primary/15 px-3 py-2 font-semibold text-primary-foreground shadow hover:border-primary/60 hover:bg-primary/25"
					>
						Start maintenance window
					</BitsButton.Root>
				{:else}
					<BitsButton.Root
						class="rounded-lg border border-muted-foreground/40 bg-muted/20 px-3 py-2 font-semibold text-muted-foreground"
						disabled
					>
						Controls locked
					</BitsButton.Root>
				{/if}
			</div>
		</div>
	</section>

	<section class="grid gap-4 md:grid-cols-2">
		{#each filteredServers as server}
			<a
				href={`/servers/${server.slug}`}
				class="block rounded-2xl border border-muted-foreground/30 bg-white/70 p-5 shadow-lg transition hover:-translate-y-1 hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5"
			>
				<div class="flex items-start justify-between gap-4">
					<div>
						<p class="mono text-xs text-muted-foreground">{server.address}</p>
						<p class="text-lg font-semibold">{server.name}</p>
						<p class="text-sm text-muted-foreground">{server.region} · {server.version}</p>
					</div>
					<span
						class={cn(
							'rounded-md px-2 py-1 text-[11px] font-semibold tracking-[0.18em] uppercase',
							server.status === 'online' &&
								'bg-emerald-500/15 text-emerald-600 dark:text-emerald-200',
							server.status === 'degraded' && 'bg-amber-500/15 text-amber-600 dark:text-amber-100',
							server.status === 'offline' && 'bg-red-500/15 text-red-600 dark:text-red-100'
						)}
					>
						{server.status}
					</span>
				</div>
				<div class="mt-3 grid gap-2 text-sm text-muted-foreground md:grid-cols-3">
					<div>
						<span class="font-semibold text-foreground">{server.playerCount}</span> / {server.maxPlayers}
						players
					</div>
					<div>
						Latency: <span class="font-semibold text-foreground">{server.latencyMs}ms</span>
					</div>
					<div>Uptime: <span class="font-semibold text-foreground">{server.uptime}</span></div>
				</div>
				<Separator.Root class="my-3 border-muted-foreground/20" />
				<div class="flex flex-wrap gap-2 text-[11px]">
					{#each server.tags as tag}
						<span
							class="rounded-md border border-muted-foreground/30 bg-white/70 px-2 py-1 font-semibold shadow-inner dark:bg-white/10"
							>{tag}</span
						>
					{/each}
				</div>
				{#if allowed}
					<div
						class="mt-3 rounded-lg border border-primary/30 bg-primary/10 px-3 py-2 text-xs text-primary-foreground"
					>
						Privileged users can open console streaming, send commands, and review per-server player
						timelines.
					</div>
				{/if}
			</a>
		{/each}
	</section>
</div>
