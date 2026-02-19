<script lang="ts">
	import { login, authStore } from '$lib/auth';
	import { goto } from '$app/navigation';
	import Button from "$lib/components/ui/button/Button.svelte";

	let username = '';
	let password = '';
	let error = '';
	let loading = false;

	async function handleLogin() {
		error = '';
		loading = true;

		try {
			await login(username, password);
			await goto('/'); // Redirect to home page after successful login
		} catch (e) {
			error = e instanceof Error ? e.message : 'Login failed';
		} finally {
			loading = false;
		}
	}
</script>

<svelte:head>
	<title>Login Page</title>
	<meta name="description" content="Login page" />
</svelte:head>

<div class="flex min-h-dvh items-center justify-center">
	<div class="w-full max-w-md space-y-8 rounded-lg border p-8">
		<div>
			<h2 class="text-center text-3xl font-bold">Sign in to Nexus</h2>
		</div>

		<form on:submit|preventDefault={handleLogin} class="space-y-6">
			{#if error}
				<div class="rounded bg-red-100 p-3 text-red-700">
					{error}
				</div>
			{/if}

			<div>
				<label for="username" class="block text-sm font-medium">Username</label>
				<input
					id="username"
					name="username"
					type="text"
					required
					bind:value={username}
					class="mt-1 block w-full rounded border px-3 py-2"
					placeholder="Enter your username"
				/>
			</div>

			<div>
				<label for="password" class="block text-sm font-medium">Password</label>
				<input
					id="password"
					name="password"
					type="password"
					required
					bind:value={password}
					class="mt-1 block w-full rounded border px-3 py-2"
					placeholder="Enter your password"
				/>
			</div>

			<Button type="submit" disabled={loading} variant="default">
				{loading ? 'Signing in...' : 'Sign in'}
			</Button>
		</form>

		<div class="text-center text-sm">
			Don't have an account?
			<a href="/register" class="text-blue-600 hover:underline">Register</a>
		</div>
	</div>
</div>

