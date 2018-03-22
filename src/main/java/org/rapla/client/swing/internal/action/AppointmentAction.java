/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.internal.action;

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.swing.RaplaAction;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

public class AppointmentAction extends RaplaAction implements Consumer<PopupContext> {
    public final static int DELETE = 1;
    public final static int COPY = 2;
    public final static int PASTE = 3;
    public final static int CUT = 4;
    public final static int EDIT = 6;
    public final static int VIEW = 7;
    public final static int CHANGE_ALLOCATABLE = 8;
    public final static int ADD_TO_RESERVATION = 9;
    public final static int PASTE_AS_NEW = 10;
    public final static int DELETE_SELECTION = 11;
    
    PopupContext popupContext;
    int type;
    AppointmentBlock appointmentBlock;

	ReservationEdit reservationEdit;
//    ReservationWizard wizard;
	private Collection<Allocatable> contextAllocatables;
	private final CalendarSelectionModel calendarSelectionModel;
    protected final ReservationController reservationController;
    private final EditController editController;
    private final InfoFactory infoFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final PermissionController permissionController;
    RaplaResources i18n;
    
	public AppointmentAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,PopupContext popupContext, CalendarSelectionModel calendarSelectionModel, ReservationController reservationController,EditController editController, InfoFactory infoFactory, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.i18n = i18n;
        this.editController = editController;
        this.popupContext = popupContext;
        this.calendarSelectionModel = calendarSelectionModel;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
    }

    public AppointmentAction setAddTo(ReservationEdit reservationEdit) throws RaplaException
    {
    	this.reservationEdit = reservationEdit;
        this.type = ADD_TO_RESERVATION;
        String name2 = getName(reservationEdit.getReservation());
		
        String value = name2.trim().length() > 0 ? "'" + name2 + "'" : getString("new_reservation");
		putValue(NAME, value);
        setIcon(getI18n().getIcon("icon.new"));
        final RaplaFacade raplaFacade = getFacade();
        boolean canAllocate = raplaFacade.canAllocate(calendarSelectionModel, getUser());
        setEnabled( canAllocate);
        return this;
    }

    public AppointmentAction setCopy(AppointmentBlock appointmentBlock, Collection<Allocatable> contextAllocatables) throws RaplaException {
        this.appointmentBlock = appointmentBlock;
        this.type = COPY;
        this.contextAllocatables = contextAllocatables;
        putValue(NAME, getString("copy"));
        setIcon( i18n.getIcon("icon.copy"));
        setEnabled(permissionController.canCreateReservation(getUser()));
        return this;
    }

    public AppointmentAction setCut(AppointmentBlock appointmentBlock, Collection<Allocatable> contextAllocatables) throws RaplaException {
        this.appointmentBlock = appointmentBlock;
        this.type = CUT;
        this.contextAllocatables = contextAllocatables;
        putValue(NAME, getString("cut"));
        setIcon( i18n.getIcon("icon.cut"));
        setEnabled(permissionController.canCreateReservation(getUser()));
        return this;
    }

    
    public AppointmentAction setPaste( ) throws RaplaException {
        this.type = PASTE;
        putValue(NAME, getString("paste_into_existing_event"));
        setIcon( i18n.getIcon("icon.paste"));
        setEnabled(isAppointmentOnClipboard() && permissionController.canCreateReservation(getUser()));
        return this;
    }

    public AppointmentAction setPasteAsNew( ) throws RaplaException {
        this.type = PASTE_AS_NEW;
        putValue(NAME, getString("paste_as") + " " + getString( "new_reservation" ) );
        setIcon( i18n.getIcon("icon.paste_new"));
        setEnabled(isAppointmentOnClipboard() && permissionController.canCreateReservation(getUser()));
        return this;
    }

    /**
     * Context menu entry to delete an appointment.
     */
    public AppointmentAction setDelete(AppointmentBlock appointmentBlock) throws RaplaException {
    	this.appointmentBlock = appointmentBlock;
    	Appointment appointment = appointmentBlock.getAppointment();
    	this.type = DELETE;
    	putValue(NAME, i18n.format("delete.format", getString("appointment")));
        setIcon( i18n.getIcon("icon.delete"));
    	setEnabled(permissionController.canModify(appointment.getReservation(), getUser()));
    	return this;
    }
    
    public AppointmentAction setDeleteSelection(Collection<AppointmentBlock> selection) throws RaplaException {
        this.type = DELETE_SELECTION;
        putValue(NAME, getString("delete_selection"));
        setIcon( i18n.getIcon("icon.delete"));
        changeSelection( selection );
        return this;
    }

    Collection<AppointmentBlock> blockList;
    
    private void changeSelection(Collection<AppointmentBlock> blockList) throws RaplaException {
    	
    	this.blockList = blockList;
        final RaplaFacade raplaFacade = getFacade();
    	if (type == DELETE_SELECTION) {
        	boolean enabled = true;
             if (blockList != null && blockList.size() > 0 ) {
                 Iterator<AppointmentBlock> it = blockList.iterator();
                 while (it.hasNext()) {
                     if (!permissionController.canModify(it.next().getAppointment().getReservation(), getUser())){
                         enabled = false;
                         break;
                     }
                 }
             } else {
                 enabled = false;
             }
             setEnabled(enabled);		
         }
	}

	public AppointmentAction setView(AppointmentBlock appointmentBlock) {
        this.appointmentBlock = appointmentBlock;
        Appointment appointment = appointmentBlock.getAppointment();
        this.type = VIEW;
        putValue(NAME, getString("view"));
        setIcon(i18n.getIcon("icon.help"));
        try 
        {
        	User user = getUser();
            boolean canRead = permissionController.canRead(appointment, user);
            setEnabled( canRead);
        } 
        catch (RaplaException ex)
        {
            getLogger().error( "Can't get user",ex);
        }
        return this;
    }

    public AppointmentAction setEdit(AppointmentBlock appointmentBlock) throws RaplaException {
        this.appointmentBlock = appointmentBlock;
        this.type = EDIT;
        putValue(SMALL_ICON, i18n.getIcon("icon.edit"));
        Appointment appointment = appointmentBlock.getAppointment();
        boolean canExchangeAllocatables = getQuery().canExchangeAllocatables(getUser(),appointment.getReservation());
		boolean canModify = permissionController.canModify(appointment.getReservation(), getUser());
		String text = !canModify && canExchangeAllocatables ?  getString("exchange_allocatables") : getString("edit");
		putValue(NAME, text);
		setEnabled(canModify || canExchangeAllocatables );
        return this;
    }

    @Override
    public void accept(PopupContext popupContext) {
        actionPerformed();
    }

    public void actionPerformed() {
        try {
            switch (type) {
            case DELETE: delete();break;
            case COPY: copy();break;
            case CUT: cut();break;
            case PASTE: paste(false);break;
            case PASTE_AS_NEW: paste( true);break;
          //  case NEW: newReservation();break;
            case ADD_TO_RESERVATION: addToReservation();break;
            case EDIT: edit();break;
            case VIEW: view();break;
            case DELETE_SELECTION: deleteSelection();break;
            }
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex,popupContext);
        } // end of try-catch
    }
    
    protected ReservationController getReservationController()
    {
        return reservationController;
    }

    private void deleteSelection() {
    	if ( this.blockList == null){
    		return;
    	}
    	handleException(reservationController.deleteBlocks(blockList,popupContext));
	}

    public void view() throws RaplaException {
        Appointment appointment = appointmentBlock.getAppointment();
    	infoFactory.showInfoDialog(appointment.getReservation(), popupContext);
    }

    public void edit()  {
        editController.edit( appointmentBlock, popupContext);
    }

    private void delete()  {
        handleException(reservationController.deleteAppointment(appointmentBlock,popupContext));
    }

    private void copy()
    {
        handleException(reservationController.copyAppointmentBlock(appointmentBlock,popupContext, contextAllocatables));
    }
    
    private void cut()
    {
       handleException(reservationController.cutAppointment(appointmentBlock,popupContext, contextAllocatables));
    }

    protected Promise handleException(Promise promise)
    {
        return promise.exceptionally(ex->
                    dialogUiFactory.showException((Throwable)ex,popupContext)
        );
    }

    private void paste(boolean asNewReservation) throws RaplaException {
        
		ReservationController reservationController = getReservationController();
        Date start = getStartDate(calendarSelectionModel);
    	boolean keepTime = !calendarSelectionModel.isMarkedIntervalTimeEnabled();
    	handleException(reservationController.pasteAppointment(	start
                                               ,popupContext
                                               ,asNewReservation, keepTime));
    }

    private void addToReservation() throws RaplaException
    {
    	Date start = getStartDate(calendarSelectionModel);
    	Date end = getEndDate(calendarSelectionModel, start);
    	handleException(reservationEdit.addAppointment(start,end));
    }

    public boolean isAppointmentOnClipboard() {
        return (getReservationController().isAppointmentOnClipboard());
    }
    



}
