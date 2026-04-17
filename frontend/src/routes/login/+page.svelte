<script lang="ts">
	import { goto } from '$app/navigation';
	import { Button as BitsButton } from 'bits-ui';
	import { login } from '$lib/auth';
	import { isValidUsername, normalizeUsername } from '$lib/utils';

	let username = '';
	let displayName = '';
	let password = '';
	let error = '';
	let loading = false;

	async function handleLogin(event: SubmitEvent) {
		event.preventDefault();
		error = '';
		loading = true;

		try {
			await login(username, displayName, password);
			await goto('/');
		} catch (e) {
			error = e instanceof Error ? e.message : 'Login failed';
		} finally {
			loading = false;
		}
	}
</script>

<svelte:head>
	<title>Sign in | Nexus Control</title>
	<meta name="description" content="Login with username and display name" />
</svelte:head>

<div class="flex min-h-dvh items-center justify-center px-4">
	<div
		class="w-full max-w-xl space-y-6 rounded-2xl border border-muted-foreground/20 bg-white/70 p-8 shadow-2xl backdrop-blur dark:border-white/10 dark:bg-white/5"
	>
		<div class="space-y-2 text-center">
			<p class="heading-label">Authentication</p>
			<h1 class="text-2xl font-semibold">Sign in to Nexus</h1>
			<p class="text-sm text-muted-foreground">
				Login requires both username and display name. Usernames must be lowercase.
			</p>
		</div>

		<form class="space-y-5" onsubmit={handleLogin}>
			{#if error}
				<div
					class="rounded-lg border border-destructive/50 bg-destructive/10 px-3 py-2 text-sm text-destructive"
				>
					{error}
				</div>
			{/if}

			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Username</span>
				<input
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					placeholder="lowercase only"
					value={username}
					oninput={(event) => (username = normalizeUsername(event.currentTarget.value))}
					required
				/>
				{#if !isValidUsername(username) && username.length}
					<div class="text-xs text-destructive">
						Use only lowercase letters, numbers, dots, underscores, and dashes.
					</div>
				{:else}
					<div class="text-xs text-muted-foreground">
						Usernames can be changed later but must stay unique.
					</div>
				{/if}
			</label>

			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Display name</span>
				<input
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					placeholder="Visible name used during login"
					value={displayName}
					oninput={(event) => (displayName = event.currentTarget.value)}
					required
				/>
			</label>

			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Password</span>
				<input
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					type="password"
					placeholder="••••••••"
					value={password}
					oninput={(event) => (password = event.currentTarget.value)}
					required
				/>
			</label>

			<BitsButton.Root
				type="submit"
				class="w-full rounded-lg border border-primary/50 bg-primary/15 px-4 py-2 text-sm font-semibold text-primary-foreground shadow hover:border-primary/60 hover:bg-primary/25 disabled:cursor-not-allowed disabled:opacity-60"
				disabled={loading || !username || !displayName || !password || !isValidUsername(username)}
			>
				{loading ? 'Signing in…' : 'Sign in'}
			</BitsButton.Root>
		</form>

		<div class="text-center text-sm text-muted-foreground">
			Don't have an account?
			<a href="/register" class="font-semibold text-primary underline-offset-4 hover:underline"
				>Create one</a
			>
		</div>
	</div>
</div>
