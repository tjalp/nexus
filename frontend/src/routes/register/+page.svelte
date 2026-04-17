<script lang="ts">
	import { goto } from '$app/navigation';
	import { Button as BitsButton } from 'bits-ui';
	import { register } from '$lib/auth';
	import { isValidUsername, normalizeUsername } from '$lib/utils';

	let username = '';
	let displayName = '';
	let password = '';
	let profileId = '';
	let error = '';
	let loading = false;

	async function handleRegister(event: SubmitEvent) {
		event.preventDefault();
		error = '';
		loading = true;

		try {
			await register(username, displayName, password, profileId);
			await goto('/');
		} catch (e) {
			error = e instanceof Error ? e.message : 'Registration failed';
		} finally {
			loading = false;
		}
	}
</script>

<svelte:head>
	<title>Create account | Nexus Control</title>
	<meta name="description" content="Create an account to manage Nexus network" />
</svelte:head>

<div class="flex min-h-dvh items-center justify-center px-4">
	<div
		class="w-full max-w-2xl space-y-6 rounded-2xl border border-muted-foreground/20 bg-white/70 p-8 shadow-2xl backdrop-blur dark:border-white/10 dark:bg-white/5"
	>
		<div class="space-y-2 text-center">
			<p class="heading-label">Account creation</p>
			<h1 class="text-2xl font-semibold">Create your Nexus account</h1>
			<p class="text-sm text-muted-foreground">
				Usernames must be lowercase and unique. Display names are required for login.
			</p>
		</div>

		<form class="grid gap-5 md:grid-cols-2" onsubmit={handleRegister}>
			{#if error}
				<div
					class="rounded-lg border border-destructive/50 bg-destructive/10 px-3 py-2 text-sm text-destructive md:col-span-2"
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
				<div class="text-xs text-muted-foreground">
					Lowercase, unique, and can be changed later.
				</div>
				{#if username && !isValidUsername(username)}
					<div class="text-xs text-destructive">Allowed: a-z, 0-9, ., _, -</div>
				{/if}
			</label>

			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Display name</span>
				<input
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					placeholder="Used during login"
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
					placeholder="Strong password"
					value={password}
					oninput={(event) => (password = event.currentTarget.value)}
					required
				/>
			</label>

			<label class="space-y-1 text-sm">
				<span class="text-muted-foreground">Profile ID (link your player)</span>
				<input
					class="w-full rounded-lg border border-muted-foreground/30 bg-white/80 px-3 py-2 text-sm shadow-inner transition outline-none placeholder:text-muted-foreground/70 focus:border-primary focus:ring-2 focus:ring-primary/30 dark:bg-white/5"
					placeholder="e.g. UUID or record id"
					value={profileId}
					oninput={(event) => (profileId = event.currentTarget.value)}
					required
				/>
				<div class="text-xs text-muted-foreground">
					We link accounts to profiles. Replace with your real profile id when wiring the backend.
				</div>
			</label>

			<div class="flex flex-wrap items-center justify-between gap-3 md:col-span-2">
				<div class="text-xs text-muted-foreground">
					Log in later using both username and display name. Server management is limited to
					privileged roles.
				</div>
				<BitsButton.Root
					type="submit"
					class="rounded-lg border border-primary/50 bg-primary/15 px-4 py-2 text-sm font-semibold text-primary-foreground shadow hover:border-primary/60 hover:bg-primary/25 disabled:cursor-not-allowed disabled:opacity-60"
					disabled={loading ||
						!username ||
						!displayName ||
						!password ||
						!profileId ||
						!isValidUsername(username)}
				>
					{loading ? 'Creating…' : 'Create account'}
				</BitsButton.Root>
			</div>
		</form>

		<div class="text-center text-sm text-muted-foreground">
			Already have an account?
			<a href="/login" class="font-semibold text-primary underline-offset-4 hover:underline"
				>Sign in</a
			>
		</div>
	</div>
</div>
