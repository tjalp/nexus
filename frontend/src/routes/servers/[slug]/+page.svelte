<script lang="ts">
	import { Button as BitsButton } from 'bits-ui';
	import { canManageServers, userRole } from '$lib/auth';
	import { cn } from '$lib/utils';
	import type { PageData } from './$types';

	export let data: PageData;

	let allowed = false;
	let role = 'viewer';
	let activityPeak = Math.max(...data.server.activity.map((point) => point.players), 1);
	let command = '';

	$: allowed = $canManageServers;
	$: role = $userRole ?? 'viewer';
	$: activityPeak = Math.max(...data.server.activity.map((point) => point.players), 1);
</script>

<svelte:head>
	<title>Nexus Control | {data.server.name}</title>
	<meta name="description" content="Server diagnostics and console" />
</svelte:head>

<div class="space-y-8">
	<header class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
		<div>
			<p class="heading-label">Server detail</p>
			<h1 class="text-2xl font-semibold">{data.server.name}</h1>
			<p class="text-sm text-muted-foreground">
				{data.server.region} · {data.server.version} · {data.server.address}
			</p>
		</div>
		<div class="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
			<span class="rounded-md border border-muted-foreground/40 bg-muted/20 px-2 py-1"
				>Role: {role}</span
			>
			<span class="rounded-md border border-muted-foreground/40 bg-muted/20 px-2 py-1"
				>Status: {data.server.status}</span
			>
			<span class="rounded-md border border-muted-foreground/40 bg-muted/20 px-2 py-1"
				>Players: {data.server.playerCount}/{data.server.maxPlayers}</span
			>
		</div>
	</header>

	{#if !allowed}
		<div
			class="panel border-dashed border-amber-400/60 bg-amber-500/10 text-sm text-amber-900 dark:text-amber-50"
		>
			Console streaming and commands are locked. Only privileged users can trigger actions. Metrics
			below remain visible for everyone.
		</div>
	{/if}

	<section class="grid gap-4 md:grid-cols-4">
		<div class="panel">
			<p class="heading-label">Players</p>
			<p class="mt-1 text-3xl font-semibold">{data.server.playerCount}</p>
			<p class="text-xs text-muted-foreground">Capacity {data.server.maxPlayers}</p>
		</div>
		<div class="panel">
			<p class="heading-label">Latency</p>
			<p class="mt-1 text-3xl font-semibold">{data.server.latencyMs}ms</p>
			<p class="text-xs text-muted-foreground">Live ping to proxy</p>
		</div>
		<div class="panel">
			<p class="heading-label">TPS</p>
			<p class="mt-1 text-3xl font-semibold">{data.server.tps}</p>
			<p class="text-xs text-muted-foreground">Server tick rate</p>
		</div>
		<div class="panel">
			<p class="heading-label">Uptime</p>
			<p class="mt-1 text-3xl font-semibold">{data.server.uptime}</p>
			<p class="text-xs text-muted-foreground">Last restart</p>
		</div>
	</section>

	<section class="grid gap-6 lg:grid-cols-3">
		<div class="space-y-4 lg:col-span-2">
			<div class="panel space-y-2">
				<div class="flex items-center justify-between">
					<div>
						<p class="heading-label">Console output</p>
						<p class="text-sm text-muted-foreground">
							Read-only stream; not connected to the server for now.
						</p>
					</div>
					{#if allowed}
						<BitsButton.Root
							class="rounded-lg border border-primary/50 bg-primary/15 px-3 py-2 text-sm font-semibold text-primary-foreground shadow hover:border-primary/60 hover:bg-primary/25"
						>
							Attach live stream
						</BitsButton.Root>
					{/if}
				</div>
				<div
					class="mono h-64 overflow-auto rounded-lg border border-black/50 bg-black/90 p-3 text-[12px] text-emerald-200 shadow-inner"
				>
					{#each data.server.consoleFeed as log}
						<div class="flex gap-3">
							<span class="text-amber-200">{log.at}</span>
							<span
								class={cn(
									log.level === 'INFO' && 'text-emerald-200',
									log.level === 'WARN' && 'text-amber-200',
									log.level === 'ERROR' && 'text-red-300'
								)}>{log.level}</span
							>
							<span>{log.message}</span>
						</div>
					{/each}
				</div>
				<div class="flex flex-wrap items-center gap-3 text-sm">
					<input
						class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 md:w-auto md:flex-1 dark:bg-white/5"
						placeholder="Type a command (disabled until wired)"
						value={command}
						oninput={(event) => (command = event.currentTarget.value)}
						disabled={!allowed}
					/>
					<BitsButton.Root
						class={cn(
							'rounded-lg px-3 py-2 text-sm font-semibold shadow',
							allowed
								? 'border border-primary/50 bg-primary/15 text-primary-foreground hover:border-primary/60 hover:bg-primary/25'
								: 'border border-muted-foreground/40 bg-muted/20 text-muted-foreground'
						)}
						disabled={!allowed}
					>
						Send
					</BitsButton.Root>
				</div>
			</div>

			<div class="panel space-y-3">
				<div class="flex items-center justify-between">
					<div>
						<p class="heading-label">Player timeline</p>
						<p class="text-sm text-muted-foreground">Recent player counts (not live)</p>
					</div>
					<span
						class="mono rounded-md border border-muted-foreground/30 bg-muted/20 px-2 py-1 text-xs text-muted-foreground"
						>Peak {activityPeak}</span
					>
				</div>
				<div class="grid grid-cols-6 gap-2">
					{#each data.server.activity as point}
						<div class="flex flex-col items-center gap-1">
							<div class="flex h-32 w-full items-end rounded-md bg-muted/20">
								<div
									class="w-full rounded-md bg-gradient-to-t from-primary/50 to-primary/80"
									style={`height: ${Math.max(12, (point.players / activityPeak) * 120)}px`}
								></div>
							</div>
							<span class="mono text-[11px] text-muted-foreground">{point.label}</span>
						</div>
					{/each}
				</div>
			</div>
		</div>

		<div class="space-y-4">
			<div class="panel space-y-3">
				<p class="heading-label">Players online</p>
				{#if data.server.players.length === 0}
					<p class="text-sm text-muted-foreground">No one is connected right now.</p>
				{:else}
					<ul class="space-y-3">
						{#each data.server.players as player}
							<li
								class="rounded-lg border border-muted-foreground/30 bg-white/80 p-3 shadow-inner dark:bg-white/5"
							>
								<div class="flex items-center justify-between">
									<div>
										<p class="text-sm font-semibold">{player.displayName}</p>
										<p class="mono text-xs text-muted-foreground">@{player.username}</p>
									</div>
									<span
										class={cn(
											'rounded-md px-2 py-1 text-[11px] font-semibold tracking-[0.18em] uppercase',
											player.status === 'online' &&
												'bg-emerald-500/15 text-emerald-600 dark:text-emerald-200',
											player.status === 'idle' &&
												'bg-amber-500/15 text-amber-600 dark:text-amber-100',
											player.status === 'recent' &&
												'bg-blue-500/15 text-blue-600 dark:text-blue-100'
										)}>{player.status}</span
									>
								</div>
								<div class="mt-2 text-xs text-muted-foreground">
									Ping: <span class="font-semibold text-foreground">{player.ping}ms</span> · Last
									seen: {player.lastSeen}
								</div>
							</li>
						{/each}
					</ul>
				{/if}
			</div>

			<div class="panel space-y-3">
				<p class="heading-label">Connected peers</p>
				{#each data.peers as peer}
					<div
						class="flex items-center justify-between rounded-lg border border-muted-foreground/30 bg-white/70 px-3 py-2 text-sm shadow-inner dark:bg-white/5"
					>
						<div>
							<p class="font-semibold">{peer.name}</p>
							<p class="mono text-[11px] text-muted-foreground">{peer.address}</p>
						</div>
						<a
							class="text-primary underline-offset-2 hover:underline"
							href={`/servers/${peer.slug}`}>View</a
						>
					</div>
				{/each}
			</div>
		</div>
	</section>
</div>
