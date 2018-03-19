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
package org.rapla.client.swing.internal.view;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.TreeToolTipRenderer;
import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.iterator.FilterIterable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.SortedClassifiableComparator;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

@Singleton
@DefaultImplementation(of = TreeFactory.class, context = InjectionContext.swing)
public class TreeFactoryImpl extends RaplaGUIComponent implements TreeFactory
{
    private final InfoFactory infoFactory;
    private final RaplaImages raplaImages;

    Icon bigFolderPeriods;
    Icon bigFolderResourcesFiltered;
    Icon bigFolderResourcesUnfiltered;
    Icon bigFolderEvents;
    Icon bigFolderCategories;
    Icon bigFolderConflicts;
    Icon defaultIcon;
    Icon personIcon;
    Icon folderClosedIcon;
    Icon folderOpenIcon;
    Icon forbiddenIcon;
    Font normalFont = UIManager.getFont("Tree.font");
    Font bigFont = normalFont.deriveFont(Font.BOLD, (float) (normalFont.getSize() * 1.2));

    @Inject
    public TreeFactoryImpl(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, InfoFactory infoFactory, RaplaImages raplaImages)
    {
        super(facade, i18n, raplaLocale, logger);
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        bigFolderPeriods = raplaImages.getIconFromKey("icon.big_folder_periods");
        bigFolderResourcesFiltered = raplaImages.getIconFromKey("icon.big_folder_resources_filtered");
        bigFolderResourcesUnfiltered = raplaImages.getIconFromKey("icon.big_folder_resources");
        bigFolderEvents = raplaImages.getIconFromKey("icon.big_folder_events");
        bigFolderCategories = raplaImages.getIconFromKey("icon.big_folder_categories");
        bigFolderConflicts = raplaImages.getIconFromKey("icon.big_folder_conflicts");
        defaultIcon = raplaImages.getIconFromKey("icon.tree.default");
        personIcon = raplaImages.getIconFromKey("icon.tree.persons");
        folderClosedIcon = raplaImages.getIconFromKey("icon.folder");
        folderOpenIcon = raplaImages.getIconFromKey("icon.folder");
        forbiddenIcon = raplaImages.getIconFromKey("icon.no_perm");

    }

    class DynamicTypeComperator implements Comparator<DynamicType>
    {
        public int compare(DynamicType o1, DynamicType o2)
        {
            int rang1 = getRang(o1);
            int rang2 = getRang(o2);
            if (rang1 < rang2)
            {
                return -1;
            }
            if (rang1 > rang2)
            {
                return 1;
            }
            return compareIds((DynamicTypeImpl) o1, (DynamicTypeImpl) o2);
        }

        private int compareIds(DynamicTypeImpl o1, DynamicTypeImpl o2)
        {
            return o1.compareTo(o2);
        }

        private int getRang(DynamicType o1)
        {
            String t2 = o1.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (t2 != null && t2.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
            {
                return 1;
            }
            if (t2 != null && t2.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
            {
                return 2;
            }
            else
            {
                return 3;
            }
        }
    }

    public TreeModel createClassifiableModel(Allocatable[] classifiables, boolean useCategorizations)
    {
        @SuppressWarnings({ "rawtypes" })
        Comparator<Classifiable> comp = new SortedClassifiableComparator(getLocale());
        return createClassifiableModel(classifiables, comp, useCategorizations);
    }

    private TreeModel createClassifiableModel(Classifiable[] classifiables, Comparator<Classifiable> comp, boolean useCategorizations)
    {
        Set<DynamicType> typeSet = new LinkedHashSet<DynamicType>();
        for (Classifiable classifiable : classifiables)
        {
            DynamicType type = classifiable.getClassification().getType();
            typeSet.add(type);
        }
        List<DynamicType> typeList = new ArrayList<DynamicType>(typeSet);
        Collections.sort(typeList, new DynamicTypeComperator());
        Map<DynamicType, NamedNode> nodeMap = new HashMap<>();
        for (DynamicType type : typeList)
        {
            NamedNode node = newNamedNode(type);
            nodeMap.put(type, node);
        }

        Set<Classifiable> sortedClassifiable = new TreeSet<Classifiable>(comp);
        sortedClassifiable.addAll(Arrays.asList(classifiables));
        addClassifiables(nodeMap, sortedClassifiable, useCategorizations);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
        for (DynamicType type : typeList)
        {
            DefaultMutableTreeNode typeNode = nodeMap.get(type);
            root.add(typeNode);
        }
        return new DefaultTreeModel(root);
    }

    private Map<Classifiable, Collection<NamedNode>> addClassifiables(Map<DynamicType, NamedNode> nodeMap,
            Collection<? extends Classifiable> classifiables, boolean useCategorizations)
    {
        Map<DynamicType, Map<Object, NamedNode>> categorization = new LinkedHashMap<>();
        Map<Classifiable, Collection<NamedNode>> childMap = new HashMap<Classifiable, Collection<NamedNode>>();
        Map<Classifiable, Collection<Classifiable>> belongsToMap = new HashMap<Classifiable, Collection<Classifiable>>();
        Map<Classifiable, NamedNode> objectToNamedNode = new HashMap<>();
        Map<DynamicType, Collection<NamedNode>> uncategorized = new LinkedHashMap<DynamicType, Collection<NamedNode>>();
        for (DynamicType type : nodeMap.keySet())
        {
            categorization.put(type, new LinkedHashMap<>());
            uncategorized.put(type, new ArrayList<>());
        }
        for (Iterator<? extends Classifiable> it = classifiables.iterator(); it.hasNext(); )
        {
            Classifiable classifiable = it.next();
            Classification classification = classifiable.getClassification();
            Collection<NamedNode> childNodes = new ArrayList<NamedNode>();
            childMap.put(classifiable, childNodes);
            DynamicType type = classification.getType();
            Assert.notNull(type);
            NamedNode typeNode = nodeMap.get(type);
            // type not found, could be because type is not visible
            if (typeNode == null)
            {
                continue;
            }
            Attribute categorizationAtt = getCategorizationAttribute(classification);
            Attribute belongsAtt = ((DynamicTypeImpl) classification.getType()).getBelongsToAttribute();
            if (belongsAtt != null && classification.getValueForAttribute(belongsAtt) != null)
            {
                Classifiable parent = (Classifiable) classification.getValueForAttribute(belongsAtt);
                Collection<Classifiable> parts = belongsToMap.get(parent);
                if (parts == null)
                {
                    parts = new ArrayList<Classifiable>();
                    belongsToMap.put(parent, parts);
                }
                parts.add(classifiable);
            }
            if (useCategorizations && categorizationAtt != null && classification.getValues(categorizationAtt).size() > 0)
            {
                Collection<Object> values = classification.getValues(categorizationAtt);
                for (Object value : values)
                {
                    NamedNode childNode = newNamedNode(classifiable);
                    childNodes.add(childNode);
                    Map<Object, NamedNode> map = categorization.get(type);
                    NamedNode parentNode = map.get(value);
                    if (parentNode == null)
                    {
                        String name = getName(value);
                        parentNode = newNamedNode(new Categorization(name));
                        map.put(value, parentNode);
                    }
                    parentNode.add(childNode);
                }
            }
            else
            {
                NamedNode childNode = newNamedNode( classifiable);
                objectToNamedNode.put(classifiable, childNode);
                childNodes.add(childNode);
                Assert.notNull(typeNode);
                uncategorized.get(type).add(childNode);
                if (useCategorizations)
                {
                    fillPackages(classification, childNode);
                }
            }

        }
        for (DynamicType type : categorization.keySet())
        {
            NamedNode parentNode = nodeMap.get(type);
            //Attribute categorizationAtt = type.getAttribute("categorization");
            Map<Object, NamedNode> map = categorization.get(type);
            Collection<Object> sortedCats = getSortedCategorizations(map.keySet());
            for (Object cat : sortedCats)
            {
                NamedNode childNode = map.get(cat);
                parentNode.add(childNode);
            }
        }

        for (DynamicType type : uncategorized.keySet())
        {
            NamedNode parentNode = nodeMap.get(type);
            for (NamedNode node : uncategorized.get(type))
            {
                parentNode.add(node);
            }
        }
        if (useCategorizations)
        {
            for (Classifiable classifiable : classifiables)
            {
                final NamedNode node = objectToNamedNode.get(classifiable);
                if (node != null)
                {
                    addBelongsToNodes(belongsToMap, classifiable, node);
                }
            }
        }
        return childMap;
    }

    private void fillPackages(Classification classification, NamedNode node)
    {
        final Attribute packagesAttribute = ((DynamicTypeImpl) classification.getType()).getPackagesAttribute();
        if (packagesAttribute != null)
        {
            final Collection<Object> values = classification.getValues(packagesAttribute);
            for (Object target : values)
            {
                if (target instanceof Classifiable)
                {
                    final Classifiable classifiable = (Classifiable) target;
                    NamedNode childNode = newNamedNode((Named) classifiable);
                    node.add(childNode);
                    final Classification childClassification = classifiable.getClassification();
                    fillPackages(childClassification, childNode);
                }
            }
        }
    }

    private void addBelongsToNodes(Map<Classifiable, Collection<Classifiable>> belongsToMap, Classifiable classifiable, final NamedNode node)
    {
        final Collection<Classifiable> parts = belongsToMap.get(classifiable);
        if (parts != null)
        {
            for (Classifiable belongsTo : parts)
            {
                final NamedNode newChildNode = newNamedNode(belongsTo);
                node.add(newChildNode);
                addBelongsToNodes(belongsToMap, belongsTo, newChildNode);
            }
        }
    }

    protected Attribute getCategorizationAttribute(Classification classification)
    {
        for (Attribute attribute : classification.getType().getAttributeIterable())
        {
            String annotation = attribute.getAnnotation(AttributeAnnotations.KEY_CATEGORIZATION);
            if (annotation != null && annotation.equals("true"))
            {
                return attribute;
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Collection<Object> getSortedCategorizations(Collection<Object> unsortedCats)
    {
        ArrayList<Comparable> sortableCats = new ArrayList<Comparable>();
        ArrayList<Object> unsortableCats = new ArrayList<Object>();
        // All attribute values should implement Comparable but for the doubts we test if value is not comparable
        for (Object cat : unsortedCats)
        {
            if (cat instanceof Comparable)
            {
                sortableCats.add((Comparable<?>) cat);
            }
            else
            {
                unsortableCats.add(cat);
            }
        }
        Collections.sort(sortableCats);
        List<Object> allCats = new ArrayList<Object>(sortableCats);
        allCats.addAll(unsortableCats);
        return allCats;
    }

    class Categorization implements Comparable<Categorization>,Named
    {
        String cat;

        public Categorization(String cat)
        {
            this.cat = cat.intern();
        }

        public String toString()
        {
            return cat;
        }

        public boolean equals(Object obj)
        {
            return cat.equals(obj.toString());
        }

        public int hashCode()
        {
            return cat.hashCode();
        }

        public int compareTo(Categorization o)
        {
            return cat.compareTo(o.cat);
        }

        @Override
        public String getName(Locale locale) {
            return cat;
        }
    }


    private boolean isInFilter(ClassificationFilter[] filter, DynamicType type)
    {
        if (filter == null)
            return true;
        for (int i = 0; i < filter.length; i++)
        {
            if (filter[i].getType().equals(type))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasRulesFor(ClassificationFilter[] filter, DynamicType type)
    {
        if (filter == null)
            return false;
        for (int i = 0; i < filter.length; i++)
        {
            if (filter[i].getType().equals(type) && filter[i].ruleSize() > 0)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the Resources root
     *
     * @param filter
     * @return
     * @throws RaplaException
     */
    public TypeNode createResourcesModel(ClassificationFilter[] filter) throws RaplaException
    {
        TypeNode treeNode = newTypeNode(Allocatable.class, CalendarModelImpl.ALLOCATABLES_ROOT);
        Map<DynamicType, NamedNode> nodeMap = new HashMap<>();

        boolean resourcesFiltered = false;

        DynamicType[] types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        for (int i = 0; i < types.length; i++)
        {
            DynamicType type = types[i];
            if (hasRulesFor(filter, type))
            {
                resourcesFiltered = true;
            }
            if (!isInFilter(filter, type))
            {
                resourcesFiltered = true;
                continue;
            }

            NamedNode node = newNamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }

        // creates typ folders
        types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
        for (int i = 0; i < types.length; i++)
        {
            DynamicType type = types[i];
            if (hasRulesFor(filter, type))
            {
                resourcesFiltered = true;
            }
            if (!isInFilter(filter, type))
            {
                resourcesFiltered = true;
                continue;
            }

            NamedNode node = newNamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }

        treeNode.setFiltered(resourcesFiltered);

        // adds elements to typ folders
        Allocatable[] filtered = getQuery().getAllocatablesWithFilter(filter);
        Collection<Allocatable> sorted = sorted(filtered, new SortedClassifiableComparator(getLocale()));
        addClassifiables(nodeMap, sorted, true);
        return treeNode;
    }

    private Collection<Allocatable> sorted(Allocatable[] allocatables, Comparator<Classifiable> comp)
    {

        Map<String, Set<Allocatable>> typeSet = new LinkedHashMap<>();
        for (Allocatable alloc : allocatables)
        {
            final String key = alloc.getClassification().getType().getKey();
            Set<Allocatable> sortedSet = typeSet.get(key);
            if (sortedSet == null)
            {
                sortedSet = new TreeSet<>(comp);
                typeSet.put(key, sortedSet);
            }
            sortedSet.add(alloc);
        }
        LinkedHashSet<Allocatable> sortedList = new LinkedHashSet<>();
        for (Set<Allocatable> sortedSubset : typeSet.values())
        {
            sortedList.addAll(sortedSubset);
        }
        return sortedList;
    }

    public TypeNode createReservationTypeModel() throws RaplaException
    {
        TypeNode treeNode = newTypeNode(Reservation.class, getString("reservation_type"));

        // creates typ folders
        DynamicType[] types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        for (int i = 0; i < types.length; i++)
        {
            DynamicType type = types[i];

            NamedNode node = newNamedNode(type);
            treeNode.add(node);
        }
        treeNode.setFiltered(false);
        return treeNode;
    }

    @SuppressWarnings("deprecation")
    public DefaultTreeModel createModel(ClassificationFilter[] filter) throws RaplaException
    {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");

        // Resources and Persons
        //  Add the resource types
        //   Add the resources
        //  Add the person types
        //    Add the persons
        TypeNode resourceRoot = createResourcesModel(filter);
        root.add(resourceRoot);
        User[] userList = getQuery().getUsers();
        final User workingUser = getUser();
        final boolean isAdmin = workingUser.isAdmin();
        final Category userGroupsCategory = getQuery().getUserGroupsCategory();
        if (isAdmin)
        {
            TypeNode reservationsRoot = createReservationTypeModel();
            root.add(reservationsRoot);
            NamedNode categoryRoot = createRootNode(Collections.singleton(getQuery().getSuperCategory()), true);
            root.add(categoryRoot);
            final int indexOfGroupsCategory = categoryRoot.getIndexOfUserObject(userGroupsCategory);
            if (indexOfGroupsCategory >= 0)
            {
                categoryRoot.remove(indexOfGroupsCategory);
            }

            // set category root name
            MultiLanguageName multiLanguageName = getQuery().getSuperCategory().getName();
            // TODO try to replace hack
            multiLanguageName.setNameWithoutReadCheck(getI18n().getLang(), getString("categories"));
            // Add the periods    
            TypeNode periodRoot = newTypeNode(Period.class, getString("periods"));
            DynamicType periodType = getQuery().getDynamicType(StorageOperator.PERIOD_TYPE);

            Allocatable[] periodList = getQuery().getAllocatablesWithFilter(periodType.newClassificationFilter().toArray());
            Comparator<Classifiable> comp =      ( o1, o2)->
            {
                final Object start1 = o1.getClassification().getValue("start");
                final Object start2 = o2.getClassification().getValue("start");
                if (start1 != null && start2 != null && start1 instanceof Comparable)
                {
                    int result = ((Comparable) start1).compareTo(start2);
                    if (result != 0)
                    {
                        return result;
                    }
                }

                return ((Entity) o1).getId().compareTo(((Entity) o2).getId());
            };
            for (final Allocatable period : sorted(periodList, comp))
            {
                NamedNode node = newNamedNode(period);
                periodRoot.add(node);
            }
            root.add(periodRoot);
        }
        return new DefaultTreeModel(root);
    }

    private class ConflictRoot
    {
        final String text;
        int conflictNumber = 0;

        ConflictRoot(String text)
        {
            this.text = text;
        }

        public void setConflictNumber(int conflictNumber)
        {
            this.conflictNumber = conflictNumber;
        }

        @Override
        public String toString()
        {
            String result = getI18n().format(text, conflictNumber);
            return result;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((text == null) ? 0 : text.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ConflictRoot other = (ConflictRoot) obj;
            //            if (conflictNumber != other.conflictNumber)
            //                return false;
            if (text == null)
            {
                if (other.text != null)
                    return false;
            }
            else if (!text.equals(other.text))
                return false;
            return true;
        }

    }

    public DefaultTreeModel createConflictModel(Collection<Conflict> conflicts) throws RaplaException
    {
        TypeNode rootNode = newTypeNode(Conflict.class, "root");
        ConflictRoot conflictRootObj = new ConflictRoot("conflictUC");
        TypeNode treeNode = newTypeNode(Conflict.class, conflictRootObj);
        rootNode.add(treeNode);
        if (conflicts != null)
        {
            {
                Iterable<Conflict> filteredConflicts = filter(conflicts, true);
                int conflict_number = addConflicts(filteredConflicts, treeNode);
                conflictRootObj.setConflictNumber(conflict_number);
            }
            {
                Iterable<Conflict> filteredConflicts = filter(conflicts, false);
                ConflictRoot conflictDisabledRootObj = new ConflictRoot("disabledConflictUC");
                TypeNode treeNode2 = newTypeNode(Conflict.class, conflictDisabledRootObj);
                int conflict_number = addConflicts(filteredConflicts, treeNode2);
                if (conflict_number > 0)
                {
                    conflictDisabledRootObj.setConflictNumber(conflict_number);
                    rootNode.add(treeNode2);
                }
            }
        }
        return new DefaultTreeModel(rootNode);
    }


    private Iterable<Conflict> filter(Iterable<Conflict> conflicts, final boolean enabledState)
    {
        return new FilterIterable<Conflict>(conflicts)
        {
            protected boolean isInIterator(Object obj)
            {
                boolean inIterator = ((Conflict) obj).checkEnabled() == enabledState;
                return inIterator;
            }
        };
    }

    private int addConflicts(Iterable<Conflict> conflicts, TypeNode treeNode) throws RaplaException
    {
        int conflictsAdded = 0;
        Map<DynamicType, NamedNode> nodeMap = new LinkedHashMap<>();

        String[] classificationTypes = new String[] {DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON};
        for (String classificationType: classificationTypes) {
            final DynamicType[] dynamicTypes = getQuery().getDynamicTypes(classificationType);
            for (DynamicType type: dynamicTypes) {
                NamedNode node = newNamedNode(type);
                nodeMap.put(type, node);
            }
        }

        Collection<Allocatable> allocatables = new LinkedHashSet<>();
        for (Iterator<Conflict> it = conflicts.iterator(); it.hasNext(); )
        {
            Conflict conflict = it.next();
            Allocatable allocatable = conflict.getAllocatable();
            allocatables.add(allocatable);
        }
        Collection<Allocatable> sorted = sorted(allocatables.toArray(new Allocatable[] {}), new SortedClassifiableComparator(getLocale()));
        Map<Classifiable, Collection<NamedNode>> childMap = addClassifiables(nodeMap, sorted, true);
        for (Iterator<Conflict> it = conflicts.iterator(); it.hasNext(); )
        {
            Conflict conflict = it.next();
            conflictsAdded++;
            Allocatable allocatable = conflict.getAllocatable();
            for (NamedNode allocatableNode : childMap.get(allocatable))
            {
                allocatableNode.add(newNamedNode(conflict));
            }
        }
        for ( NamedNode node:nodeMap.values())
        {
            if ( node.getChildCount() > 0) {
                treeNode.add(node);
            }
        }
        return conflictsAdded;
    }

    public NamedNode newNamedNode(Named element)
    {
        return new NamedNode(element);
    }

    private TypeNode newTypeNode(Class<? extends RaplaObject> type, Object userObject) {
        return new TypeNode(type,userObject);
    }


    public TreeModel createModel(Category category)
    {
        return createModel(Collections.singleton(category), true);
    }

    public TreeModel createModel(Collection<Category> categories, boolean includeChildren)
    {
        NamedNode rootNode = createRootNode(categories, includeChildren);
        return new DefaultTreeModel(rootNode);
    }

    protected NamedNode createRootNode(Collection<Category> categories, boolean includeChildren)
    {
        Map<Category, NamedNode> nodeMap = new HashMap<Category, NamedNode>();
        Category superCategory = null;
        {
            Category persistantSuperCategory = getQuery().getSuperCategory();
            for (Category cat : categories)
            {
                if (persistantSuperCategory.equals(cat))
                {
                    superCategory = cat;
                }
            }
            if (superCategory == null)
            {
                superCategory = persistantSuperCategory;
            }
        }
        nodeMap.put(superCategory, newNamedNode(superCategory));
        LinkedHashSet<Category> uniqueCategegories = new LinkedHashSet<Category>();
        for (Category cat : categories)
        {
            if (includeChildren)
            {
                for (Category child : getAllChildren(cat))
                {
                    uniqueCategegories.add(child);
                }
            }
            uniqueCategegories.add(cat);
        }
        for (Category cat : uniqueCategegories)
        {
            NamedNode node = nodeMap.get(cat);
            if (node == null)
            {
                node = newNamedNode(cat);
                nodeMap.put(cat, node);
            }
        }

        LinkedList<Category> list = new LinkedList<Category>();
        list.addAll(uniqueCategegories);
        while (!list.isEmpty())
        {
            Category cat = list.pop();
            NamedNode node = nodeMap.get(cat);
            if (node == null)
            {
                node = newNamedNode(cat);
                nodeMap.put(cat, node);
            }
            Category parent = cat.getParent();
            if (parent != null)
            {
                NamedNode parentNode = nodeMap.get(parent);
                if (parentNode == null)
                {
                    parentNode = newNamedNode(parent);
                    nodeMap.put(parent, parentNode);
                    list.push(parent);
                    uniqueCategegories.add( parent);
                }
                parentNode.add(node);
            }
        }
        NamedNode rootNode = nodeMap.get(superCategory);
        while (true)
        {
            int childCount = rootNode.getChildCount();
            if (childCount <= 0 || childCount > 1)
            {
                break;
            }
            Category cat = (Category) rootNode.getUserObject();
            if (uniqueCategegories.contains(cat))
            {
                break;
            }
            NamedNode firstChild = (NamedNode) rootNode.getFirstChild();
            rootNode.remove(firstChild);
            rootNode = firstChild;

        }
        return rootNode;
    }

    private Collection<Category> getAllChildren(Category cat)
    {
        ArrayList<Category> result = new ArrayList<Category>();
        for (Category child : cat.getCategories())
        {
            result.add(child);
            Collection<Category> childsOfChild = getAllChildren(child);
            result.addAll(childsOfChild);
        }
        return result;
    }


    public class NamedNode extends DefaultMutableTreeNode
    {
        private static final long serialVersionUID = 1L;

        NamedNode(Named obj)
        {
            super(obj);
        }

        public String toString()
        {
            Named obj = (Named) getUserObject();
            if (obj != null)
            {
                Locale locale = getI18n().getLocale();
                if (obj instanceof Classifiable)
                {
                    Classification classification = ((Classifiable) obj).getClassification();
                    if (classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING) != null)
                    {
                        return classification.format(locale, DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING);
                    }
                }
                String name = obj.getName(locale);
                return name;
            }
            else
            {
                return super.toString();
            }
        }

        public int getIndexOfUserObject(Object object)
        {
            if (children == null)
            {
                return -1;
            }
            for (int i = 0; i < children.size(); i++)
            {
                if (((DefaultMutableTreeNode) children.get(i)).getUserObject().equals(object))
                    return i;
            }
            return -1;
        }

        public void add(NamedNode node) {
            super.add( node );
        }
    }

    class TypeNode extends DefaultMutableTreeNode
    {
        private static final long serialVersionUID = 1L;

        boolean filtered;
        Class<? extends RaplaObject> type;
        String title;

        TypeNode(Class<? extends RaplaObject> type, Object userObject)
        {
            this.type = type;
            if ( userObject == CalendarModelImpl.ALLOCATABLES_ROOT) {
                title = getString("resources");
            }   else {
                this.title = null;
            }
            setUserObject(userObject);
        }

        public Class<? extends RaplaObject> getType()
        {
            return type;
        }

        public boolean isFiltered()
        {
            return filtered;
        }

        public void setFiltered(boolean filtered)
        {
            this.filtered = filtered;
        }

        public String toString()
        {
            if (title != null)
            {
                return title;
            }
            else
            {
                return userObject.toString();
            }
        }

        public void add(TypeNode node) {
            super.add( node );
        }

        public void add(NamedNode node) {
            super.add( node );
        }
    }

    public TreeToolTipRenderer createTreeToolTipRenderer()
    {
        return new RaplaTreeToolTipRenderer();
    }

    public TreeCellRenderer createRenderer()
    {
        return new ComplexTreeCellRenderer();
    }

    public TreeCellRenderer createConflictRenderer()
    {
        return new ConflictTreeCellRenderer();
    }

    class ComplexTreeCellRenderer extends DefaultTreeCellRenderer
    {
        private static final long serialVersionUID = 1L;

        Border nonIconBorder = BorderFactory.createEmptyBorder(1, 0, 1, 0);
        Border conflictBorder = BorderFactory.createEmptyBorder(2, 0, 2, 0);

        public ComplexTreeCellRenderer()
        {
            setLeafIcon(defaultIcon);
        }

        private void setIcon(Object object, boolean leaf)
        {
            Icon icon = null;
            boolean isAllocatable = false;
            if (object instanceof Allocatable)
            {
                isAllocatable = true;
                Allocatable allocatable = (Allocatable) object;
                try
                {
                    User user = getUser();
                    Date today = getQuery().today();
                    if (!getFacade().getPermissionController().canAllocate(allocatable, user, today))
                    {
                        icon = forbiddenIcon;
                    }
                    else
                    {
                        if (allocatable.isPerson())
                        {
                            icon = personIcon;
                        }
                        else
                        {
                            icon = defaultIcon;
                        }
                    }
                }
                catch (RaplaException ex)
                {
                }

            }
            else if (object instanceof DynamicType)
            {
                DynamicType type = (DynamicType) object;
                String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(classificationType))
                {
                    setBorder(conflictBorder);
                }
                else
                {
                    icon = folderClosedIcon;
                }
            }
            if (icon == null)
            {
                setBorder(nonIconBorder);
            }
            if (leaf)
            {
                setLeafIcon(icon);
            }
            else if (isAllocatable)
            {
                setOpenIcon(icon);
                setClosedIcon(icon);
                setIcon(icon);
            }
        }

        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
        {
            setBorder(null);
            setFont(normalFont);
            if (value != null && value instanceof TypeNode)
            {
                TypeNode typeNode = (TypeNode) value;
                Icon bigFolderIcon;
                final Class<? extends RaplaObject> type = typeNode.getType();
                if (type == Period.class)
                {
                    bigFolderIcon = bigFolderPeriods;
                }
                else if (type == Reservation.class)
                {
                    bigFolderIcon = bigFolderEvents;
                }
                else
                {
                    if (typeNode.isFiltered())
                    {
                        bigFolderIcon = bigFolderResourcesFiltered;
                    }
                    else
                    {
                        bigFolderIcon = bigFolderResourcesUnfiltered;
                    }
                }
                setClosedIcon(bigFolderIcon);
                setOpenIcon(bigFolderIcon);
                setLeafIcon(bigFolderIcon);
                setFont(bigFont);
                value = typeNode.toString();
            }
            else
            {
                Object nodeInfo = getUserObject(value);
                final boolean topCategoryNode;
                if (nodeInfo instanceof Category)
                {
                    final Category category = (Category) nodeInfo;
                    final Category parent = category.getParent();
                    if (parent == null)
                    {
                        topCategoryNode = true;
                    }
                    else
                    {
                        boolean flag;
                        try
                        {
                            Category userGroupsCategory = getFacade().getUserGroupsCategory();
                            User user = getClientFacade().getUser();
                            flag = userGroupsCategory.equals(category) && (user.isAdmin() || PermissionController.getAdminGroups(user).size() > 0);
                        }
                        catch (RaplaException e)
                        {
                            getLogger().error("Could not resolve user or usergroups: " + e.getMessage(), e);
                            flag = false;
                        }
                        topCategoryNode = flag;
                    }
                }
                else
                {
                    topCategoryNode = false;
                }

                if (topCategoryNode)
                {
                    setClosedIcon(bigFolderCategories);
                    setOpenIcon(bigFolderCategories);
                    setFont(bigFont);
                }
                else
                {
                    setClosedIcon(folderClosedIcon);
                    setOpenIcon(folderOpenIcon);
                    //if (leaf) {
                    setIcon(nodeInfo, leaf);
                    //}
                }
            }
            Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            return result;
        }
    }

    class ConflictTreeCellRenderer extends DefaultTreeCellRenderer
    {
        private static final long serialVersionUID = 1L;
        Border nonIconBorder = BorderFactory.createEmptyBorder(1, 0, 1, 0);
        Border conflictBorder = BorderFactory.createEmptyBorder(2, 0, 2, 0);

        public ConflictTreeCellRenderer()
        {
            setFont(normalFont);
            setLeafIcon(null);
            setBorder(conflictBorder);
        }

        protected String getText(Conflict conflict)
        {
            StringBuffer buf = new StringBuffer();
            buf.append("<html>");
            Date startDate = conflict.getStartDate();
            RaplaLocale raplaLocale = getRaplaLocale();
            buf.append(raplaLocale.formatDate(startDate));
            if (!DateTools.cutDate(startDate).equals(startDate))
            {
                buf.append(' ');
                buf.append(raplaLocale.formatTime(startDate));
            }
            //            buf.append( getAppointmentFormater().getSummary(conflict.getAppointment1()));
            buf.append("<br>");
            buf.append(conflict.getReservation1Name());
            if (conflict.getRepeatingType1() != null)
            {
                buf.append(getRepeatingType(conflict.getRepeatingType1()));
            }
            buf.append(' ');
            buf.append(getString("with"));
            buf.append('\n');
            buf.append("<br>");
            buf.append(conflict.getReservation2Name());
            if (conflict.getRepeatingType2() != null)
            {
                buf.append(getRepeatingType(conflict.getRepeatingType2()));
            }
            // TOD add the rest of conflict
            //            buf.append( ": " );
            //            buf.append( " " );
            //            buf.append( getRaplaLocale().formatTime(conflict.getAppointment1().getStart()));
            ////            buf.append( " - ");
            ////            buf.append( getRaplaLocale().formatTime(conflict.getAppointment1().getEnd()));
            //            buf.append( "<br>" );
            //            buf.append( getString("reservation.owner") + " ");
            //            buf.append( conflict.getUser2().getUsername());
            buf.append("</html>");
            String result = buf.toString();
            return result;
        }

        private String getRepeatingType(RepeatingType repeatingType)
        {
            final String keyName = repeatingType.name().toLowerCase();
            final String reslt = getI18n().getString(keyName);
            return " [" + reslt + "]";
        }

        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
        {
            if (value != null && value instanceof TypeNode)
            {
                TypeNode typeNode = (TypeNode) value;
                setFont(bigFont);
                value = typeNode.toString();
                setIcon(bigFolderConflicts);
                setClosedIcon(bigFolderConflicts);
                setOpenIcon(bigFolderConflicts);
                leaf = false;
            }
            else
            {
                setClosedIcon(folderClosedIcon);
                setOpenIcon(folderOpenIcon);
                Object nodeInfo = getUserObject(value);
                setFont(normalFont);
                if (nodeInfo instanceof Conflict)
                {
                    Conflict conflict = (Conflict) nodeInfo;
                    String text = getText(conflict);
                    value = text;
                }
                else if (nodeInfo instanceof Allocatable)
                {
                    Allocatable allocatable = (Allocatable) nodeInfo;
                    Icon icon;
                    if (allocatable.isPerson())
                    {
                        icon = personIcon;
                    }
                    else
                    {
                        icon = defaultIcon;
                    }
                    setClosedIcon(icon);
                    setOpenIcon(icon);
                    // can't be null because nodeInfo is not null
                    @SuppressWarnings("null")
                    String text = value.toString();
                    if (value instanceof TreeNode)
                    {
                        text += " (" + getRecursiveChildCount(((TreeNode) value)) + ")";
                    }
                    value = text;
                }
                else
                {
                    String text = TreeFactoryImpl.this.getName(nodeInfo);
                    if (value instanceof TreeNode)
                    {
                        //text+= " (" + getRecursiveChildCount(((TreeNode) value)) +")";
                        text += " (" + getRecursiveList(((TreeNode) value)).size() + ")";

                    }
                    value = text;

                }
            }
            Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            return result;
        }

        private int getRecursiveChildCount(TreeNode treeNode)
        {
            int count = 0;
            int children = treeNode.getChildCount();
            if (children == 0)
            {
                return 1;
            }
            for (int i = 0; i < children; i++)
            {
                TreeNode child = treeNode.getChildAt(i);
                count += getRecursiveChildCount(child);
            }
            return count;
        }

        private Set<Conflict> getRecursiveList(TreeNode treeNode)
        {
            int children = treeNode.getChildCount();
            if (children == 0)
            {
                return Collections.emptySet();
            }
            HashSet<Conflict> set = new HashSet<Conflict>();
            for (int i = 0; i < children; i++)
            {
                TreeNode child = treeNode.getChildAt(i);
                Object userObject = ((DefaultMutableTreeNode) child).getUserObject();
                if (userObject != null && userObject instanceof Conflict)
                {
                    set.add((Conflict) userObject);
                }
                else
                {
                    set.addAll(getRecursiveList(child));
                }
            }
            return set;
        }

    }

    public TreeSelectionModel createComplexTreeSelectionModel()
    {
        return new DelegatingTreeSelectionModel()
        {
            private static final long serialVersionUID = 1L;

            boolean isSelectable(TreePath treePath)
            {
                Object lastPathComponent = treePath.getLastPathComponent();
                Object object = getUserObject(lastPathComponent);
                return !(object instanceof Categorization);
            }
        };
    }

    public TreeSelectionModel createConflictTreeSelectionModel()
    {
        return new DelegatingTreeSelectionModel()
        {
            private static final long serialVersionUID = 1L;

            boolean isSelectable(TreePath treePath)
            {
                Object lastPathComponent = treePath.getLastPathComponent();
                Object object = getUserObject(lastPathComponent);
                if (object instanceof Conflict)
                {
                    return true;
                }
                if (object instanceof Allocatable)
                {
                    return true;
                }
                return object instanceof DynamicType;
            }
        };
    }

    private abstract class DelegatingTreeSelectionModel extends DefaultTreeSelectionModel
    {
        abstract boolean isSelectable(TreePath treePath);

        private static final long serialVersionUID = 1L;

        private TreePath[] getSelectablePaths(TreePath[] pathList)
        {
            List<TreePath> result = new ArrayList<TreePath>(pathList.length);
            for (TreePath treePath : pathList)
            {
                if (isSelectable(treePath))
                {
                    result.add(treePath);
                }
            }
            return result.toArray(new TreePath[result.size()]);
        }

        @Override
        public void setSelectionPath(TreePath path)
        {
            if (isSelectable(path))
            {
                super.setSelectionPath(path);
            }
        }

        @Override
        public void setSelectionPaths(TreePath[] paths)
        {
            paths = getSelectablePaths(paths);
            super.setSelectionPaths(paths);
        }

        @Override
        public void addSelectionPath(TreePath path)
        {
            if (isSelectable(path))
            {
                super.addSelectionPath(path);
            }
        }

        @Override
        public void addSelectionPaths(TreePath[] paths)
        {
            paths = getSelectablePaths(paths);
            super.addSelectionPaths(paths);
        }

    }

    private static Object getUserObject(Object node)
    {
        if (node instanceof DefaultMutableTreeNode)
            return ((DefaultMutableTreeNode) node).getUserObject();
        return node;
    }

    class RaplaTreeToolTipRenderer implements TreeToolTipRenderer
    {
        public String getToolTipText(JTree tree, int row)
        {
            Object node = tree.getPathForRow(row).getLastPathComponent();
            Object value = getUserObject(node);
            if (value instanceof Conflict)
            {
                return null;
            }
            return infoFactory.getToolTip(value);
        }
    }

}
