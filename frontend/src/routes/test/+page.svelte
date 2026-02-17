<script lang="ts">
    import Button from "$lib/components/ui/button/Button.svelte";
    import {getContextClient, queryStore} from "@urql/svelte";
    import {graphql} from "$lib/gql";
    import type {GeneralAttachment} from "$lib/gql/graphql";

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

    let name = $derived(generalAttachment?.lastKnownName ?? "No name");
</script>

<svelte:head>
    <title>Home</title>
    <meta name="description" content="Test page" />
</svelte:head>

{#if $profileQuery.fetching}
    <Button class="max-w-sm" disabled variant="secondary">Loading...</Button>
{:else if $profileQuery.error}
    <Button class="max-w-sm" disabled variant="destructive">Error: {$profileQuery.error.message}</Button>
{:else if $profileQuery.data?.profile}
    <Button class="max-w-sm" href="/profile/{name}">{name}</Button>
{:else}
    <Button class="max-w-sm" disabled variant="destructive">No profile found</Button>
{/if}

<style>
</style>
