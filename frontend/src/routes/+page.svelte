<script lang="ts">
	import { Button as BitsButton, Separator, Tabs } from 'bits-ui';
	import { isAuthenticated, userRole } from '$lib/auth';
	import { cn, isValidUsername, normalizeUsername } from '$lib/utils';
	import type { PageData } from './$types';

	export let data: PageData;

	let search = '';
	let selectedProfile = data.profiles[0];
	let desiredUsername = selectedProfile.username;
	let desiredDisplayName = selectedProfile.displayName;
	let appealReference = '';
	let appealSummary = '';
	let appealType: 'ban' | 'mute' | 'warning' = 'ban';

	$: searchTerm = search.trim().toLowerCase();
	$: filteredProfiles = data.profiles
		.filter((profile) => {
			if (!searchTerm) return true;
			return (
				profile.username.toLowerCase().includes(searchTerm) ||
				profile.displayName.toLowerCase().includes(searchTerm)
			);
		})
		.slice(0, 6);

	$: selectedProfile && (desiredUsername = normalizeUsername(selectedProfile.username));
	$: selectedProfile && (desiredDisplayName = selectedProfile.displayName);

	$: totalPlayers = data.servers.reduce((sum, server) => sum + server.playerCount, 0);
	$: totalOnlineServers = data.servers.filter((server) => server.status !== 'offline').length;
	$: openAppeals = data.appeals.filter((appeal) => appeal.status !== 'closed');
	$: activeRole = $userRole;
	$: privileged = activeRole
		? data.privilegedRoles.includes(activeRole as (typeof data.privilegedRoles)[number])
		: false;

	function handleSelect(profile: (typeof data.profiles)[number]) {
		selectedProfile = profile;
		search = profile.username;
	}

	function handleUsernameInput(value: string) {
		desiredUsername = normalizeUsername(value);
	}
</script>

<svelte:head>
	<title>Nexus Control | Overview</title>
	<meta name="description" content="Nexus network management overview" />
</svelte:head>

<div class="space-y-8">
	<section class="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
		<div class="panel">
			<p class="heading-label">Players Online</p>
			<p class="mt-2 text-3xl font-semibold">{totalPlayers}</p>
			<p class="text-xs text-muted-foreground">Across {data.servers.length} connected nodes</p>
		</div>
		<div class="panel">
			<p class="heading-label">Servers healthy</p>
			<p class="mt-2 text-3xl font-semibold">{totalOnlineServers}/{data.servers.length}</p>
			<p class="text-xs text-muted-foreground">
				Only privileged users can open full server controls
			</p>
		</div>
		<div class="panel">
			<p class="heading-label">Open appeals</p>
			<p class="mt-2 text-3xl font-semibold">{openAppeals.length}</p>
			<p class="text-xs text-muted-foreground">Appeals stay visible to all signed-in users</p>
		</div>
		<div class="panel panel-strong">
			<p class="heading-label">Access policy</p>
			<p class="mt-2 text-lg font-semibold">Roles: {data.privilegedRoles.join(', ')}</p>
			<p class="text-xs text-muted-foreground">
				Only these roles can open the server management dashboard.
			</p>
		</div>
	</section>

	<section class="grid gap-6 lg:grid-cols-3">
		<div class="panel space-y-4">
			<div class="flex items-center justify-between">
				<div>
					<p class="heading-label">Directory</p>
					<h2 class="text-lg font-semibold">Search public profiles</h2>
				</div>
				<span
					class="mono rounded-md border border-muted-foreground/30 bg-muted/20 px-2 py-1 text-xs text-muted-foreground"
					>Public</span
				>
			</div>

			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Lookup by username or display name</span>
				<input
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					placeholder="lowercase usernames only"
					value={search}
					oninput={(event) => (search = normalizeUsername(event.currentTarget.value))}
				/>
			</label>

			<div class="flex flex-wrap gap-2">
				{#each filteredProfiles as profile}
					<BitsButton.Root
						class={cn(
							'rounded-lg border px-3 py-2 text-sm transition hover:-translate-y-0.5',
							profile.username === selectedProfile.username
								? 'border-primary/50 bg-primary/15 text-primary-foreground'
								: 'border-muted-foreground/30 bg-white/60 text-foreground hover:border-primary/40 hover:bg-primary/10 dark:bg-white/5'
						)}
						onclick={() => handleSelect(profile)}
					>
						<div class="flex items-center gap-2">
							<span class="mono text-xs">{profile.id}</span>
							<span class="font-semibold">{profile.displayName}</span>
						</div>
						<div class="text-[11px] text-muted-foreground">@{profile.username}</div>
					</BitsButton.Root>
				{/each}
			</div>

			<div
				class="rounded-lg border border-muted-foreground/20 bg-muted/10 px-3 py-2 text-xs text-muted-foreground"
			>
				Everyone can view public profile data. Administrators see attachment data and can edit
				metadata.
			</div>
		</div>

		<div class="panel lg:col-span-2">
			<div class="flex items-center justify-between gap-2">
				<div>
					<p class="heading-label">Profile</p>
					<h2 class="text-xl font-semibold">{selectedProfile.displayName}</h2>
					<p class="text-sm text-muted-foreground">@{selectedProfile.username}</p>
				</div>
				<div class="flex items-center gap-2 text-xs text-muted-foreground">
					<span class="rounded-md border border-muted-foreground/40 bg-muted/20 px-2 py-1"
						>Role: {selectedProfile.role}</span
					>
					<span class="rounded-md border border-muted-foreground/40 bg-muted/20 px-2 py-1"
						>Timezone: {selectedProfile.timezone}</span
					>
				</div>
			</div>

			<Tabs.Root value="overview" class="mt-4 flex flex-col gap-4">
				<Tabs.List class="flex flex-wrap gap-2">
					<Tabs.Trigger
						value="overview"
						class="rounded-md border border-muted-foreground/30 px-3 py-2 text-sm font-semibold data-[state=active]:border-primary/60 data-[state=active]:bg-primary/10"
					>
						Overview
					</Tabs.Trigger>
					<Tabs.Trigger
						value="attachments"
						class="rounded-md border border-muted-foreground/30 px-3 py-2 text-sm font-semibold data-[state=active]:border-primary/60 data-[state=active]:bg-primary/10"
					>
						Attachments
					</Tabs.Trigger>
					<Tabs.Trigger
						value="security"
						class="rounded-md border border-muted-foreground/30 px-3 py-2 text-sm font-semibold data-[state=active]:border-primary/60 data-[state=active]:bg-primary/10"
					>
						Security
					</Tabs.Trigger>
				</Tabs.List>

				<Tabs.Content
					value="overview"
					class="rounded-xl border border-muted-foreground/20 bg-white/60 p-4 shadow-inner dark:bg-white/5"
				>
					<div class="grid gap-4 md:grid-cols-2">
						<div class="space-y-2">
							<p class="text-xs tracking-[0.2em] text-muted-foreground uppercase">Public fields</p>
							<div
								class="rounded-lg border border-muted-foreground/20 bg-white/80 p-3 text-sm leading-relaxed shadow-inner dark:bg-white/5"
							>
								<div class="mono text-xs text-muted-foreground">Profile ID</div>
								<div class="mono text-base">{selectedProfile.id}</div>
								<div class="mt-2 text-sm text-muted-foreground">
									Primary server: <span class="font-semibold text-foreground"
										>{selectedProfile.primaryServer}</span
									>
								</div>
								<div class="text-sm text-muted-foreground">
									Reputation: <span class="font-semibold text-foreground"
										>{selectedProfile.reputation}</span
									>
								</div>
								<div class="text-sm text-muted-foreground">
									Last seen: <span class="font-semibold text-foreground"
										>{selectedProfile.lastSeen}</span
									>
								</div>
							</div>
						</div>
						<div class="space-y-3">
							<p class="text-xs tracking-[0.2em] text-muted-foreground uppercase">Username rules</p>
							<label class="space-y-1 text-sm">
								<span class="text-muted-foreground">Username (lowercase, unique, changeable)</span>
								<input
									class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
									value={desiredUsername}
									oninput={(event) => handleUsernameInput(event.currentTarget.value)}
									aria-invalid={!isValidUsername(desiredUsername)}
								/>
								{#if !isValidUsername(desiredUsername)}
									<div class="text-xs text-destructive">
										Usernames must be lowercase and contain only a-z, 0-9, ., _, -
									</div>
								{:else}
									<div class="text-xs text-muted-foreground">
										Usernames can be changed but must remain unique.
									</div>
								{/if}
							</label>
							<label class="space-y-1 text-sm">
								<span class="text-muted-foreground">Display name (used during login)</span>
								<input
									class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
									value={desiredDisplayName}
									oninput={(event) => (desiredDisplayName = event.currentTarget.value)}
								/>
								<div class="text-xs text-muted-foreground">
									Login requires both username and display name.
								</div>
							</label>
							<BitsButton.Root
								class="rounded-lg border border-primary/40 bg-primary/10 px-3 py-2 text-sm font-semibold text-primary-foreground transition hover:border-primary/60 hover:bg-primary/20"
							>
								Save change (wire backend)
							</BitsButton.Root>
						</div>
					</div>
				</Tabs.Content>

				<Tabs.Content
					value="attachments"
					class="rounded-xl border border-muted-foreground/20 bg-white/60 p-4 shadow-inner dark:bg-white/5"
				>
					{#if privileged}
						<div class="grid gap-4 md:grid-cols-2">
							<div class="space-y-2">
								<p class="text-xs tracking-[0.2em] text-muted-foreground uppercase">Admin notes</p>
								<ul class="space-y-2 text-sm leading-relaxed text-foreground">
									{#each selectedProfile.attachments.notes as note}
										<li
											class="rounded-md border border-muted-foreground/20 bg-white/70 px-3 py-2 shadow-inner dark:bg-white/5"
										>
											{note}
										</li>
									{/each}
								</ul>
							</div>
							<div class="space-y-2">
								<p class="text-xs tracking-[0.2em] text-muted-foreground uppercase">
									Security flags
								</p>
								<ul class="space-y-2 text-sm leading-relaxed text-foreground">
									{#each selectedProfile.attachments.securityFlags as flag}
										<li
											class="rounded-md border border-primary/30 bg-primary/10 px-3 py-2 text-primary-foreground shadow-inner"
										>
											{flag}
										</li>
									{/each}
								</ul>
							</div>
						</div>
					{:else}
						<div
							class="rounded-lg border border-muted-foreground/30 bg-muted/15 p-4 text-sm text-muted-foreground"
						>
							You need an elevated role to view attachment data. Contact an operator if you need to
							review or edit this profile.
						</div>
					{/if}
				</Tabs.Content>

				<Tabs.Content
					value="security"
					class="rounded-xl border border-muted-foreground/20 bg-white/60 p-4 shadow-inner dark:bg-white/5"
				>
					<div class="grid gap-4 md:grid-cols-3">
						<div class="rounded-lg border border-muted-foreground/30 bg-muted/15 p-3 text-sm">
							<p class="heading-label">Status</p>
							<p class="mt-1 font-semibold text-foreground">{selectedProfile.lastSeen}</p>
							<p class="text-xs text-muted-foreground">Last activity</p>
						</div>
						<div class="rounded-lg border border-muted-foreground/30 bg-muted/15 p-3 text-sm">
							<p class="heading-label">Tags</p>
							<div class="mt-2 flex flex-wrap gap-2">
								{#each selectedProfile.tags as tag}
									<span
										class="rounded-md border border-muted-foreground/30 bg-white/70 px-2 py-1 text-[11px] font-semibold shadow-inner dark:bg-white/5"
									>
										{tag}
									</span>
								{/each}
							</div>
						</div>
						<div class="rounded-lg border border-muted-foreground/30 bg-muted/15 p-3 text-sm">
							<p class="heading-label">Connections</p>
							<p class="mono mt-1 text-sm text-foreground">
								Discord: {selectedProfile.attachments.connections.discord ?? '—'}
							</p>
							<p class="mono text-sm">
								GitHub: {selectedProfile.attachments.connections.github ?? '—'}
							</p>
						</div>
					</div>
				</Tabs.Content>
			</Tabs.Root>
		</div>
	</section>

	<section class="grid gap-6 lg:grid-cols-2">
		<div class="panel space-y-4">
			<div class="flex items-center justify-between">
				<div>
					<p class="heading-label">Submit appeal</p>
					<h3 class="text-lg font-semibold">Anyone can file an appeal</h3>
				</div>
				<span
					class="rounded-md border border-muted-foreground/30 bg-muted/20 px-2 py-1 text-xs text-muted-foreground"
					>Channel: web</span
				>
			</div>
			<div class="grid gap-3 md:grid-cols-2">
				<label class="space-y-1 text-sm">
					<span class="text-muted-foreground">Username</span>
					<input
						class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
						placeholder="lowercase"
						value={selectedProfile.username}
						readonly
					/>
				</label>
				<label class="space-y-1 text-sm">
					<span class="text-muted-foreground">Reference (optional)</span>
					<input
						class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
						placeholder="PN-12345"
						value={appealReference}
						oninput={(event) => (appealReference = event.currentTarget.value)}
					/>
				</label>
				<label class="space-y-1 text-sm">
					<span class="text-muted-foreground">Type</span>
					<select
						class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
						value={appealType}
						onchange={(event) => (appealType = event.currentTarget.value as typeof appealType)}
					>
						<option value="ban">Ban</option>
						<option value="mute">Mute</option>
						<option value="warning">Warning</option>
					</select>
				</label>
				<label class="space-y-1 text-sm">
					<span class="text-muted-foreground">Display name (required for login)</span>
					<input
						class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
						placeholder="Display name"
						value={desiredDisplayName}
						oninput={(event) => (desiredDisplayName = event.currentTarget.value)}
					/>
				</label>
			</div>
			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Appeal details</span>
				<textarea
					class="h-28 w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					placeholder="Explain what happened and why you are appealing."
					value={appealSummary}
					oninput={(event) => (appealSummary = event.currentTarget.value)}
				></textarea>
			</label>
			<div class="flex flex-wrap items-center gap-3">
				<BitsButton.Root
					class="rounded-lg border border-primary/40 bg-primary/10 px-4 py-2 text-sm font-semibold text-primary-foreground shadow transition hover:border-primary/60 hover:bg-primary/20"
				>
					Submit appeal (wire backend)
				</BitsButton.Root>
				<span class="text-xs text-muted-foreground"
					>Appeals stay open to moderators; you can file without elevated access.</span
				>
			</div>
		</div>

		<div class="panel space-y-4">
			<div class="flex items-center justify-between">
				<div>
					<p class="heading-label">Connected servers</p>
					<h3 class="text-lg font-semibold">Preview without control</h3>
				</div>
				<BitsButton.Root
					href="/servers"
					class="rounded-lg border border-muted-foreground/40 bg-white/70 px-3 py-2 text-sm font-semibold shadow hover:border-primary/50 hover:bg-primary/10 dark:bg-white/10"
				>
					Open dashboard
				</BitsButton.Root>
			</div>
			<div class="space-y-3">
				{#each data.servers.slice(0, 3) as server}
					<div
						class="rounded-2xl border border-muted-foreground/30 bg-white/70 p-4 shadow-inner dark:bg-white/5"
					>
						<div class="flex items-center justify-between">
							<div>
								<p class="mono text-xs text-muted-foreground">{server.address}</p>
								<p class="text-base font-semibold">{server.name}</p>
							</div>
							<span
								class={cn(
									'rounded-md px-2 py-1 text-[11px] font-semibold tracking-[0.18em] uppercase',
									server.status === 'online' &&
										'bg-emerald-500/15 text-emerald-600 dark:text-emerald-200',
									server.status === 'degraded' &&
										'bg-amber-500/15 text-amber-600 dark:text-amber-100',
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
							<div>TPS: <span class="font-semibold text-foreground">{server.tps}</span></div>
						</div>
					</div>
				{/each}
			</div>
			<Separator.Root class="border-muted-foreground/30" />
			<div class="text-sm text-muted-foreground">
				Server management is locked to privileged roles. You can still view public metrics and
				player counts.
			</div>
		</div>
	</section>
</div>
