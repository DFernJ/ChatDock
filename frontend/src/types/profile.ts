export interface ProfileDTO {
    id: string;
    username: string;
    authRole: string;
    permissionRole: string;
    discordLinked: boolean;
    discordUsername: string | null;
    githubLinked: boolean;
    githubUsername: string | null;
}
