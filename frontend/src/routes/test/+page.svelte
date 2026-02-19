<script lang="ts">
    import Button from "$lib/components/ui/button/Button.svelte";
    import {getContextClient, queryStore} from "@urql/svelte";
    import {graphql} from "$lib/gql";
    import type {GeneralAttachment, PunishmentAttachment} from "$lib/gql/graphql";

    const profileQuery = queryStore({
        client: getContextClient(),
        query: graphql(/* GraphQL */`
            query getProfile($id: UUID!) {
                profile(id: $id) {
                    id
                    attachments {
                        ... on GeneralAttachment {
                            lastKnownName
                        }
                        ... on PunishmentAttachment {
                            punishments {
                                type
                                reason
                                isActive
                            }
                        }
                    }
                }
            }
        `),
        variables: {
            id: "ee903b7f-5371-44b0-bc5a-c1ef66262101"
        }
    })

    let generalAttachment = $derived(
        $profileQuery.data?.profile?.attachments?.find(
            (attachment) => attachment.__typename === 'GeneralAttachment'
        ) as GeneralAttachment | undefined
    );

    let punishmentAttachment = $derived(
        $profileQuery.data?.profile?.attachments?.find(
            (attachment) => attachment.__typename === 'PunishmentAttachment'
        ) as PunishmentAttachment | undefined
    );

    let name = $derived(generalAttachment?.lastKnownName ?? "No name");
    let punishments = $derived(punishmentAttachment?.punishments ?? []);
</script>

<svelte:head>
    <title>Test Page</title>
    <meta name="description" content="Test page" />
</svelte:head>

{#if $profileQuery.fetching}
    <Button class="max-w-sm" disabled variant="secondary">Loading...</Button>
{:else if $profileQuery.error}
    <Button class="max-w-sm" disabled variant="destructive">Error: {$profileQuery.error.message}</Button>
{:else if $profileQuery.data?.profile}
    <Button class="max-w-sm" onclick={() => {
        profileQuery.reexecute({
            requestPolicy: 'network-only'
        })
    }}>{name}</Button>
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {#each punishments as punishment}
            <Button variant={punishment.isActive ? "default" : "destructive"}>{punishment.type} - {punishment.reason}</Button>
        {/each}
    </div>
{:else}
    <Button class="max-w-sm" disabled variant="destructive">No profile found</Button>
{/if}

<style>
</style>
