export interface NotificationDTO {
    id: string;
    containerId: string;
    containerName: string;
    exitCode: number | null;
    finishedAt: string;
    message: string;
    createdAt: string;
    read: boolean;
}
