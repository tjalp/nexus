<script lang="ts">
	import './layout.css';
	import { page } from '$app/stores';
	import { browser } from '$app/environment';
	import { goto } from '$app/navigation';
	import { Button as BitsButton, Command } from 'bits-ui';
	import { setContextClient } from '@urql/svelte';
	import { urqlClient } from '$lib/urql-client';
	import { authStore, isAuthenticated, userRole } from '$lib/auth';
	import Button from '$lib/components/ui/button/Button.svelte';
	import { cn } from '$lib/utils';
	import { onMount } from 'svelte';
	import { defaultPreferences, preferenceStore, updatePreferences } from '$lib/preferences';
	import { themes } from '$lib/theme';

	let { children } = $props();

	const navItems = [
		{ href: '/', label: 'Overview' },
		{ href: '/servers', label: 'Servers' },
		{ href: '/appeals', label: 'Appeals' },
		{ href: '/parkour', label: 'Parkour' }
	];

	let prefs = defaultPreferences;
	let paletteOpen = false;
	let paletteQuery = '';
	let mounted = false;

	const fontOptions = [
		{ id: 'jetbrains', label: 'JetBrains Mono / Plex Sans' },
		{ id: 'plex', label: 'Fira Mono / Plex Sans' }
	] as const;

	const roundnessOptions = [
		{ id: 'minimal', label: 'Sharp' },
		{ id: 'medium', label: 'Soft' },
		{ id: 'pill', label: 'Pill' }
	] as const;

	const actions = [
		{ label: 'Go to Overview', onSelect: () => goto('/') },
		{ label: 'Go to Servers', onSelect: () => goto('/servers') },
		{ label: 'Go to Appeals', onSelect: () => goto('/appeals') },
		{ label: 'Go to Parkour', onSelect: () => goto('/parkour') },
		{ label: 'Toggle Theme', onSelect: () => cycleTheme() },
		{ label: 'Log out', onSelect: () => authStore.clearAuth(), requiresAuth: true }
	];

	setContextClient(urqlClient);

	onMount(() => {
		const unsub = preferenceStore.subscribe((value) => {
			prefs = value;
		});
		if (browser) {
			const handler = (event: KeyboardEvent) => {
				const mod = event.metaKey || event.ctrlKey;
				if (mod && event.key.toLowerCase() === 'k') {
					event.preventDefault();
					paletteOpen = !paletteOpen;
				}
			};
			window.addEventListener('keydown', handler);
			mounted = true;
			return () => {
				window.removeEventListener('keydown', handler);
				unsub();
			};
		}
		return () => unsub();
	});

	const activePath = $derived($page.url.pathname);
	const session = $derived($authStore);
	const role = $derived($userRole ?? 'viewer');

	function selectTheme(id: typeof themes[number]['id']) {
		updatePreferences({ theme: id });
	}

	function selectFont(id: (typeof fontOptions)[number]['id']) {
		updatePreferences({ font: id });
	}

	function selectRoundness(id: (typeof roundnessOptions)[number]['id']) {
		updatePreferences({ radius: id });
	}

	function cycleTheme() {
		const index = themes.findIndex((t) => t.id === prefs.theme);
		const next = themes[(index + 1) % themes.length];
		selectTheme(next.id);
	}
</script>

<div class="min-h-svh bg-background text-foreground">
	<div class="grid min-h-svh grid-cols-1 gap-6 lg:grid-cols-[270px_1fr]">
		<aside class="grid-surface sticky top-0 hidden min-h-svh flex-col gap-4 border-r border-white/10 bg-white/70 p-6 backdrop-blur dark:bg-white/5 lg:flex">
			<div class="flex items-center gap-3">
				<div class="flex h-11 w-11 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-secondary text-lg font-semibold text-primary-foreground shadow">
					NX
				</div>
				<div>
					<p class="text-sm text-muted-foreground">Nexus Control</p>
					<p class="mono text-base font-semibold">Programmer mode</p>
				</div>
			</div>

			<nav class="mt-4 space-y-1" aria-label="Primary">
				{#each navItems as item}
					<BitsButton.Root
						href={item.href}
						aria-current={activePath === item.href ? 'page' : undefined}
						class={cn(
							'flex w-full items-center justify-between rounded-lg border px-3 py-2 text-sm font-semibold transition',
							activePath === item.href
								? 'border-primary/60 bg-primary/15 text-primary-foreground shadow-[0_8px_30px_-20px_rgba(37,99,235,0.6)]'
								: 'border-muted-foreground/20 bg-white/70 text-foreground hover:-translate-y-0.5 hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5'
						)}
					>
						<span class="flex items-center gap-2">
							<span class="mono text-[11px] text-muted-foreground">{item.href}</span>
							{item.label}
						</span>
						<span class="h-2 w-2 rounded-full bg-primary/70" aria-hidden="true"></span>
					</BitsButton.Root>
				{/each}
			</nav>

			<section aria-label="Themes" class="space-y-2">
				<p class="heading-label">Color themes</p>
				<div class="grid grid-cols-2 gap-2">
					{#each themes as theme}
						<button
							type="button"
							class={cn(
								'group relative rounded-lg border p-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
								theme.id === prefs.theme
									? 'border-primary/60 ring-1 ring-primary/40 bg-primary/5'
									: 'border-muted-foreground/20 bg-white/70 hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5'
							)}
							style={`--preview:${theme.preview};`}
							onclick={() => selectTheme(theme.id)}
							aria-pressed={theme.id === prefs.theme}
						>
							<div class="h-10 w-full rounded-md border border-white/20 bg-[image:var(--preview)] shadow-inner"></div>
							<p class="mt-2 text-sm font-semibold">{theme.label}</p>
							<p class="text-[11px] text-muted-foreground">{theme.isDark ? 'Dark' : 'Light'}</p>
						</button>
					{/each}
				</div>
			</section>

			<section aria-label="Fonts and roundness" class="space-y-3">
				<div class="space-y-2">
					<p class="heading-label">Font stack</p>
					<div class="space-y-2">
						{#each fontOptions as font}
							<button
								type="button"
								onclick={() => selectFont(font.id)}
								class={cn(
									'flex w-full items-center justify-between rounded-lg border px-3 py-2 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
									prefs.font === font.id
										? 'border-primary/60 bg-primary/10 text-primary-foreground'
										: 'border-muted-foreground/20 bg-white/70 hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5'
								)}
							>
								<span class="mono">{font.label}</span>
								<span class="rounded-full bg-primary/20 px-2 py-0.5 text-[10px] uppercase tracking-[0.15em] text-primary-foreground">
									Preview
								</span>
							</button>
						{/each}
					</div>
				</div>

				<div class="space-y-2">
					<p class="heading-label">UI roundness</p>
					<div class="grid grid-cols-3 gap-2">
						{#each roundnessOptions as option}
							<button
								type="button"
								onclick={() => selectRoundness(option.id)}
								class={cn(
									'rounded-lg border px-2 py-2 text-center text-xs font-semibold uppercase tracking-[0.18em] transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
									prefs.radius === option.id
										? 'border-primary/60 bg-primary/10 text-primary-foreground'
										: 'border-muted-foreground/20 bg-white/70 hover:border-primary/50 hover:bg-primary/10 dark:bg-white/5'
								)}
								style={`border-radius: ${option.id === 'minimal' ? '6px' : option.id === 'pill' ? '999px' : '14px'}`}
								aria-pressed={prefs.radius === option.id}
							>
								{option.label}
							</button>
						{/each}
					</div>
				</div>
			</section>

			<section aria-label="Session" class="mt-auto space-y-3">
				<div class="rounded-lg border border-muted-foreground/20 bg-panel p-3 shadow-inner">
					<p class="text-sm text-muted-foreground">Signed in as</p>
					<p class="mono text-base font-semibold">
						{session?.displayName ?? session?.username ?? 'Guest'}{session ? '' : ' (limited)'}
					</p>
					<p class="text-xs text-muted-foreground mt-1">Role: {role}</p>
					<div class="mt-3 flex flex-wrap gap-2">
						{#if $isAuthenticated}
							<Button variant="destructive" onclick={() => authStore.clearAuth()}>Logout</Button>
						{:else}
							<Button variant="secondary" href="/login" disabled={activePath === '/login'}>Login</Button>
							<Button variant="secondary" href="/register" disabled={activePath === '/register'}>Create</Button>
						{/if}
					</div>
				</div>
				<button
					type="button"
					class="flex w-full items-center justify-center gap-2 rounded-lg border border-muted-foreground/20 bg-white/80 px-3 py-2 text-sm font-semibold shadow hover:border-primary/50 hover:bg-primary/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 dark:bg-white/5"
					onclick={() => (paletteOpen = true)}
					aria-haspopup="dialog"
				>
					Command palette <span class="mono text-[11px] text-muted-foreground">Ctrl/Cmd + K</span>
				</button>
			</section>
		</aside>

		<div class="relative flex flex-col">
			<header class="sticky top-0 z-20 flex items-center gap-3 border-b border-white/10 bg-panel/80 px-4 py-3 backdrop-blur supports-[backdrop-filter]:backdrop-blur">
				<div class="flex items-center gap-3 lg:hidden">
					<div class="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-secondary text-sm font-semibold text-primary-foreground shadow">
						NX
					</div>
					<div class="text-left">
						<p class="text-xs uppercase tracking-[0.2em] text-muted-foreground">Nexus</p>
						<p class="mono text-sm font-semibold">Programmer mode</p>
					</div>
				</div>
				<div class="flex-1 rounded-lg border border-muted-foreground/20 bg-white/70 px-3 py-2 text-sm text-muted-foreground shadow-inner focus-within:border-primary/60 focus-within:ring-2 focus-within:ring-primary/30 dark:bg-white/5">
					<input
						class="w-full bg-transparent outline-none"
						placeholder="Type to open the command palette (Ctrl/Cmd + K)"
						onfocus={() => (paletteOpen = true)}
						aria-label="Open command palette"
					/>
				</div>
				<div class="flex items-center gap-2">
					<Button variant="secondary" href="/parkour" disabled={activePath === '/parkour'}>Parkour</Button>
					<Button variant="secondary" href="/servers" disabled={activePath === '/servers'}>Servers</Button>
				</div>
			</header>

			<main class="relative flex-1 space-y-10 px-4 py-8 lg:px-8">
				<div class="grid-surface pointer-events-none absolute inset-0 opacity-25"></div>
				<div class="relative z-10 space-y-6">
					{@render children()}
				</div>
			</main>
		</div>
	</div>

	{#if paletteOpen}
		<div class="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm" role="presentation" on:click={() => (paletteOpen = false)}></div>
		<div class="fixed inset-x-4 top-10 z-50 mx-auto max-w-2xl rounded-xl border border-muted-foreground/20 bg-panel p-3 shadow-2xl outline-none">
			<Command.Root>
				<Command.Input
					placeholder="Jump to a page or action…"
					value={paletteQuery}
					oninput={(event) => (paletteQuery = event.currentTarget.value)}
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm text-foreground shadow-inner outline-none focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
				/>
				<Command.List class="mt-3 max-h-80 overflow-auto rounded-lg border border-muted-foreground/20 bg-white/70 p-1 text-sm shadow-inner dark:bg-white/5">
					{#if paletteQuery.trim().length === 0}
						<Command.Empty class="px-3 py-2 text-muted-foreground">Start typing to filter commands.</Command.Empty>
					{/if}
					<Command.Group heading="Navigation">
						{#each actions.filter((action) => !action.requiresAuth || $isAuthenticated) as action}
							<Command.Item
								onSelect={() => {
									action.onSelect();
									paletteOpen = false;
									paletteQuery = '';
								}}
							>
								{action.label}
							</Command.Item>
						{/each}
					</Command.Group>
				</Command.List>
			</Command.Root>
		</div>
	{/if}
</div>
