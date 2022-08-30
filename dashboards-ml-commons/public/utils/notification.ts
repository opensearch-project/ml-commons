import { CoreStart } from '../../../../../src/core/public';

export const trainSuccessNotification = (notifications: CoreStart['notifications'], id: string) => notifications.toasts.addSuccess({
    title:
        "Model training successfully",
    text: `You have trained your model succesfully! Model Id is ${id} .You can go Model List. for more details.`
})