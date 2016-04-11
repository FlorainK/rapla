package org.rapla.client.internal.edit;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.event.Activity;
import org.rapla.client.event.ActivityPresenter;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.inject.Extension;
import org.rapla.inject.ExtensionRepeatable;

@Singleton
@ExtensionRepeatable({ @Extension(id = EditActivity.EDIT_EVENTS_ID, provides = ActivityPresenter.class),
        @Extension(id = EditActivity.EDIT_RESOURCES_ID, provides = ActivityPresenter.class)
}
)
public class EditActivity implements ActivityPresenter
{
    final static public String EDIT_EVENTS_ID = "editEvents";
    final static public String EDIT_RESOURCES_ID = "editResources";
    private final EditController editController;
    private  final RaplaFacade raplaFacade;
    @Inject
    public EditActivity(EditController editController, RaplaFacade facade)
    {
        this.editController = editController;
        this.raplaFacade = facade;
    }

    @Override public RaplaWidget startActivity(Activity activity)
    {
        final String activityId = activity.getId();
        String info = activity.getInfo();
        PopupContext popupContext = activity.getPopupContext();
        if (activityId.equals(EDIT_RESOURCES_ID) || activityId.equals(EDIT_EVENTS_ID))
        {
            String[] ids = ((String) info).split(",");
            List<Entity> entities = new ArrayList<>();
            Class<? extends Entity> clazz = activityId.equals(EDIT_RESOURCES_ID) ? Allocatable.class: Reservation.class;
            for (String id : ids)
            {
                final Entity resolve = raplaFacade.resolve(new ReferenceInfo(id, clazz));
                entities.add(resolve);
            }
            EditController.EditCallback<List<Entity>> callback = null;
            String title = null;
            final RaplaWidget edit = editController.edit(entities, title, popupContext, callback);
            return  edit;
        }
        else
        {
            return null;
        }
    }

    @Override public void updateView(ModificationEvent event)
    {

    }
}