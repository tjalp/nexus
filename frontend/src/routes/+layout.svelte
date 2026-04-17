<script lang="ts">
	import './layout.css';
	import { page } from '$app/stores';
	import { browser } from '$app/environment';
	import { Button as BitsButton, Separator, Switch } from 'bits-ui';
	import { setContextClient } from '@urql/svelte';
	import { urqlClient } from '$lib/urql-client';
	import { authStore, isAuthenticated, userRole } from '$lib/auth';
	import Button from '$lib/components/ui/button/Button.svelte';
	import { cn } from '$lib/utils';
	import { onMount } from 'svelte';

	let { children } = $props();

	const navItems = [
		{ href: '/', label: 'Overview' },
		{ href: '/servers', label: 'Servers' },
		{ href: '/appeals', label: 'Appeals' }
	];

	let theme = $state<'light' | 'dark'>('dark');
	let hydrated = $state(false);

	setContextClient(urqlClient);

	onMount(() => {
		hydrated = true;
		if (!browser) return;
		const stored = localStorage.getItem('theme');
		theme =
			(stored as 'light' | 'dark' | null) ??
			(matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
	});

	$effect(() => {
		if (!browser || !hydrated) return;
		const prefersDark = theme === 'dark';
		document.documentElement.classList.toggle('dark', prefersDark);
		localStorage.setItem('theme', prefersDark ? 'dark' : 'light');
	});

	const activePath = $derived($page.url.pathname);
	const session = $derived($authStore);
	const role = $derived($userRole ?? 'viewer');

	function handleThemeToggle(checked: boolean) {
		theme = checked ? 'dark' : 'light';
	}
</script>

<div class:dark={theme === 'dark'}>
	<div class="relative min-h-svh overflow-hidden bg-background text-foreground">
		<div class="grid-surface pointer-events-none absolute inset-0 opacity-40"></div>
		<div class="relative z-10 mx-auto max-w-6xl space-y-8 px-6 py-10">
			<header class="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
				<div class="space-y-3">
					<div class="flex items-center gap-3">
						<div
							class="rounded-lg border border-primary/50 bg-primary/10 px-3 py-1 text-xs font-semibold tracking-[0.35em] text-primary-foreground uppercase"
						>
							Nexus
						</div>
						<span
							class="mono rounded-md border border-muted-foreground/20 bg-muted/20 px-2 py-1 text-xs text-muted-foreground"
							>Technical Control Panel</span
						>
					</div>
					<div>
						<h1 class="text-3xl font-semibold tracking-tight">Network Operations Hub</h1>
						<p class="mt-1 max-w-2xl text-sm text-muted-foreground">
							Manage players, servers, and appeals from a single modular dashboard. Built with
							SvelteKit, Bits UI, and Tailwind.
						</p>
					</div>
					<div class="flex flex-wrap items-center gap-3">
						{#each navItems as item}
							<BitsButton.Root
								href={item.href}
								class={cn(
									'rounded-lg border px-3 py-2 text-sm font-medium transition hover:-translate-y-0.5 hover:border-primary/60 hover:bg-primary/10',
									activePath === item.href
										? 'border-primary/60 bg-primary/15 text-primary-foreground'
										: 'border-muted-foreground/20 bg-white/60 text-foreground dark:bg-white/5'
								)}
							>
								{item.label}
							</BitsButton.Root>
						{/each}
						<Separator.Root orientation="vertical" class="h-9 border-l border-muted-foreground/30" />
						<div
							class="flex items-center gap-2 rounded-lg border border-muted-foreground/20 bg-white/60 px-3 py-2 text-xs text-muted-foreground shadow-inner dark:bg-white/5"
						>
							<span class="mono tracking-[0.2em] uppercase">Theme</span>
							<Switch.Root
								aria-label="Toggle dark mode"
								checked={theme === 'dark'}
								onCheckedChange={handleThemeToggle}
								class="group relative flex h-6 w-12 items-center rounded-full border border-muted-foreground/30 bg-muted/40 px-1 transition focus-visible:ring-2 focus-visible:ring-primary/60 dark:bg-muted/30"
							>
								<Switch.Thumb
									class="pointer-events-none inline-block h-4 w-4 rounded-full bg-foreground shadow transition-all data-[state=checked]:translate-x-6 data-[state=checked]:bg-primary data-[state=unchecked]:translate-x-0 data-[state=unchecked]:bg-foreground/80"
								/>
							</Switch.Root>
						</div>
					</div>
				</div>

				<div
					class="flex flex-col gap-3 rounded-xl border border-muted-foreground/20 bg-white/70 p-4 shadow-lg backdrop-blur dark:border-white/10 dark:bg-white/5"
				>
					<div class="flex items-center gap-3">
						<div
							class="h-10 w-10 rounded-lg bg-gradient-to-br from-primary/60 to-secondary/60 shadow-inner"
						></div>
						<div>
							<p class="text-sm text-muted-foreground">Signed in as</p>
							<p class="mono text-base">
								{session?.displayName ?? session?.username ?? 'Guest'}{session
									? ''
									: ' (restricted)'}
							</p>
						</div>
					</div>
					<div class="flex items-center gap-2 text-xs text-muted-foreground">
						<span
							class="rounded-md border border-primary/30 bg-primary/10 px-2 py-1 text-primary-foreground"
							>Role: {role}</span
						>
						<span class="rounded-md border border-muted-foreground/30 bg-muted/20 px-2 py-1"
							>Endpoint ready</span
						>
					</div>
					<div class="flex flex-wrap items-center gap-2">
						{#if $isAuthenticated}
							<Button variant="destructive" onclick={() => authStore.clearAuth()}>Logout</Button>
						{:else}
							<Button variant="secondary" href="/login" disabled={activePath === '/login'}
								>Login</Button
							>
							<Button variant="secondary" href="/register" disabled={activePath === '/register'}
								>Create account</Button
							>
						{/if}
					</div>
				</div>
			</header>

			<main
				class="space-y-10 rounded-2xl border border-white/10 bg-white/60 p-6 shadow-2xl backdrop-blur dark:border-white/5 dark:bg-white/5"
			>
				{@render children()}
			</main>
		</div>
	</div>
</div>
