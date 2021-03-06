package mekhq.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.*;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SortOrder;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import megamek.common.options.PilotOptions;
import megamek.common.util.EncodeControl;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.event.PersonChangedEvent;
import mekhq.campaign.log.PersonalLogger;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.generator.SingleSpecialAbilityGenerator;
import mekhq.campaign.personnel.Skill;
import mekhq.campaign.personnel.SkillType;
import mekhq.gui.model.PersonnelTableModel;
import mekhq.gui.preferences.JComboBoxPreference;
import mekhq.gui.preferences.JIntNumberSpinnerPreference;
import mekhq.gui.preferences.JToggleButtonPreference;
import mekhq.gui.preferences.JWindowPreference;
import mekhq.gui.sorter.FormattedNumberSorter;
import mekhq.gui.sorter.RankSorter;
import mekhq.gui.utilities.MekHqTableCellRenderer;
import mekhq.preferences.PreferencesNode;

public final class BatchXPDialog extends JDialog {
    private static final long serialVersionUID = -7897406116865495209L;

    private final Campaign campaign;
    private final PersonnelTableModel personnelModel;
    private final TableRowSorter<PersonnelTableModel> personnelSorter;
    private final PersonnelFilter personnelFilter;
    private boolean dataChanged = false;

    private JTable personnelTable;
    private JComboBox<PersonTypeItem> choiceType;
    private JComboBox<PersonTypeItem> choiceExp;
    private JComboBox<String> choiceSkill;
    private JSpinner skillLevel;
    private JCheckBox allowPrisoners;
    private JButton buttonSpendXP;

    // This is used for personnel outside of Clan factions
    private final List<Integer> personnelColumns = Arrays.asList(
            PersonnelTableModel.COL_RANK,
            PersonnelTableModel.COL_GIVEN_NAME,
            PersonnelTableModel.COL_SURNAME,
            PersonnelTableModel.COL_BLOODNAME,
            PersonnelTableModel.COL_AGE,
            PersonnelTableModel.COL_TYPE,
            PersonnelTableModel.COL_XP
    );

    // The Clans do not use Surnames, but they do have Bloodnames that should be listed
    private final List<Integer> personnelClanColumns = Arrays.asList(
            PersonnelTableModel.COL_RANK,
            PersonnelTableModel.COL_GIVEN_NAME,
            PersonnelTableModel.COL_BLOODNAME,
            PersonnelTableModel.COL_AGE,
            PersonnelTableModel.COL_TYPE,
            PersonnelTableModel.COL_XP
    );

    private JLabel matchedPersonnelLabel;

    private transient ResourceBundle resourceMap;
    private transient String choiceNoSkill;

    public BatchXPDialog(JFrame parent, Campaign campaign) {
        super(parent, "", true); //$NON-NLS-1$

        this.resourceMap = ResourceBundle.getBundle("mekhq.resources.BatchXPDialog", new EncodeControl()); //$NON-NLS-1$

        setTitle(resourceMap.getString("MassTrainingDialog.title")); //$NON-NLS-1$
        choiceNoSkill = resourceMap.getString("skill.choice.text"); //$NON-NLS-1$

        this.campaign = Objects.requireNonNull(campaign);
        this.personnelModel = new PersonnelTableModel(campaign);
        personnelModel.refreshData();
        personnelSorter = new TableRowSorter<>(personnelModel);
        personnelSorter.setSortsOnUpdates(true);
        personnelSorter.setComparator(PersonnelTableModel.COL_RANK, new RankSorter(campaign));
        personnelSorter.setComparator(PersonnelTableModel.COL_AGE, new FormattedNumberSorter());
        personnelSorter.setComparator(PersonnelTableModel.COL_XP, new FormattedNumberSorter());
        personnelSorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(1, SortOrder.ASCENDING)));
        personnelFilter = new PersonnelFilter();
        personnelSorter.setRowFilter(personnelFilter);

        initComponents();

        setUserPreferences();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        add(getPersonnelTable(), BorderLayout.CENTER);
        add(getButtonPanel(), BorderLayout.WEST);

        pack();
        setLocationRelativeTo(getParent());
    }

    private void setUserPreferences() {
        PreferencesNode preferences = MekHQ.getPreferences().forClass(BatchXPDialog.class);

        choiceType.setName("primaryRole");
        preferences.manage(new JComboBoxPreference(choiceType));

        choiceExp.setName("experienceLevel");
        preferences.manage(new JComboBoxPreference(choiceExp));

        choiceSkill.setName("skill");
        preferences.manage(new JComboBoxPreference(choiceSkill));

        skillLevel.setName("skillLevel");
        preferences.manage(new JIntNumberSpinnerPreference(skillLevel));

        allowPrisoners.setName("allowPrisoners");
        preferences.manage(new JToggleButtonPreference(allowPrisoners));

        this.setName("dialog");
        preferences.manage(new JWindowPreference(this));
    }

    private JComponent getPersonnelTable() {
        personnelTable = new JTable(personnelModel);
        personnelTable.setCellSelectionEnabled(false);
        personnelTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        for(int i = PersonnelTableModel.N_COL - 1; i >= 0 ; -- i) {
            TableColumn column = personnelTable.getColumnModel().getColumn(i);
            if (campaign.getFaction().isClan()) {
                if (personnelClanColumns.contains(i)) {
                    column.setPreferredWidth(personnelModel.getColumnWidth(i));
                    column.setCellRenderer(new MekHqTableCellRenderer());
                } else {
                    personnelTable.removeColumn(column);
                }
            } else {
                if (personnelColumns.contains(i)) {
                    column.setPreferredWidth(personnelModel.getColumnWidth(i));
                    column.setCellRenderer(new MekHqTableCellRenderer());
                } else {
                    personnelTable.removeColumn(column);
                }
            }
        }
        personnelTable.setIntercellSpacing(new Dimension(1, 0));
        personnelTable.setShowGrid(false);
        personnelTable.setRowSorter(personnelSorter);

        JScrollPane pane = new JScrollPane(personnelTable);
        pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        return pane;
    }

    private JComponent getButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        choiceType = new JComboBox<>();
        choiceType.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) choiceType.getPreferredSize().getHeight()));
        DefaultComboBoxModel<PersonTypeItem> personTypeModel = new DefaultComboBoxModel<>();
        personTypeModel.addElement(new PersonTypeItem(resourceMap.getString("primaryRole.choice.text"), null)); //$NON-NLS-1$
        for(int i = 1; i < Person.T_NUM; ++ i) {
            personTypeModel.addElement(new PersonTypeItem(Person.getRoleDesc(i,campaign.getFaction().isClan()), i));
        }
        personTypeModel.addElement(new PersonTypeItem(Person.getRoleDesc(0, campaign.getFaction().isClan()), 0));
        // Add "none" for generic AsTechs
        choiceType.setModel(personTypeModel);
        choiceType.setSelectedIndex(0);
        choiceType.addActionListener(e -> {
            personnelFilter.setPrimaryRole(((PersonTypeItem) choiceType.getSelectedItem()).id);
            updatePersonnelTable();
        });
        panel.add(choiceType);

        choiceExp = new JComboBox<>();
        choiceExp.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) choiceType.getPreferredSize().getHeight()));
        DefaultComboBoxModel<PersonTypeItem> personExpModel = new DefaultComboBoxModel<>();
        personExpModel.addElement(new PersonTypeItem(resourceMap.getString("experience.choice.text"), null)); //$NON-NLS-1$
        for(int i = 0; i < 5; ++ i) {
            personExpModel.addElement(new PersonTypeItem(SkillType.getExperienceLevelName(i), i));
        }
        choiceExp.setModel(personExpModel);
        choiceExp.setSelectedIndex(0);
        choiceExp.addActionListener(e -> {
            personnelFilter.setExpLevel(((PersonTypeItem) choiceExp.getSelectedItem()).id);
            updatePersonnelTable();
        });
        panel.add(choiceExp);

        choiceSkill = new JComboBox<>();
        choiceSkill.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) choiceSkill.getPreferredSize().getHeight()));
        DefaultComboBoxModel<String> personSkillModel = new DefaultComboBoxModel<>();
        personSkillModel.addElement(choiceNoSkill);
        for(String skill : SkillType.getSkillList()) {
            personSkillModel.addElement(skill);
        }
        choiceSkill.setModel(personSkillModel);
        choiceSkill.setSelectedIndex(0);
        choiceSkill.addActionListener(e -> {
            if(choiceNoSkill.equals(choiceSkill.getSelectedItem())) {
                personnelFilter.setSkill(null);
                ((SpinnerNumberModel) skillLevel.getModel()).setMaximum(10);
                buttonSpendXP.setEnabled(false);
            } else {
                String skillName = (String) choiceSkill.getSelectedItem();
                personnelFilter.setSkill(skillName);
                int maxSkillLevel = SkillType.getType(skillName).getMaxLevel();
                int currentLevel = (Integer) skillLevel.getModel().getValue();
                ((SpinnerNumberModel) skillLevel.getModel()).setMaximum(maxSkillLevel);
                if(currentLevel > maxSkillLevel) {
                    skillLevel.getModel().setValue(maxSkillLevel);
                }
                buttonSpendXP.setEnabled(true);
            }
            updatePersonnelTable();
        });
        panel.add(choiceSkill);

        panel.add(Box.createRigidArea(new Dimension(10, 10)));
        panel.add(new JLabel(resourceMap.getString("targetSkillLevel.text"))); //$NON-NLS-1$

        skillLevel = new JSpinner(new SpinnerNumberModel(10, 0, 10, 1));
        skillLevel.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) skillLevel.getPreferredSize().getHeight()));
        skillLevel.addChangeListener(e ->
        {
            personnelFilter.setMaxSkillLevel((Integer)skillLevel.getModel().getValue());
            updatePersonnelTable();
        });
        panel.add(skillLevel);

        allowPrisoners = new JCheckBox(resourceMap.getString("allowPrisoners.text")); //$NON-NLS-1$
        allowPrisoners.setHorizontalAlignment(SwingConstants.LEFT);
        allowPrisoners.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) allowPrisoners.getPreferredSize().getHeight()));
        allowPrisoners.addChangeListener(e ->
        {
            personnelFilter.setAllowPrisoners(allowPrisoners.isSelected());
            updatePersonnelTable();
        });
        JPanel allowPrisonersPanel = new JPanel(new GridLayout(1, 1));
        allowPrisonersPanel.setAlignmentY(JComponent.LEFT_ALIGNMENT);
        allowPrisonersPanel.add(allowPrisoners);
        allowPrisonersPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) allowPrisonersPanel.getPreferredSize().getHeight()));
        panel.add(allowPrisonersPanel);

        panel.add(Box.createVerticalGlue());

        matchedPersonnelLabel = new JLabel(""); //$NON-NLS-1$
        matchedPersonnelLabel.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) matchedPersonnelLabel.getPreferredSize().getHeight()));
        panel.add(matchedPersonnelLabel);

        JPanel buttons = new JPanel(new FlowLayout());
        buttons.setMaximumSize(new Dimension(Short.MAX_VALUE, (int) buttons.getPreferredSize().getHeight()));

        buttonSpendXP = new JButton(resourceMap.getString("spendXP.text")); //$NON-NLS-1$
        buttonSpendXP.setEnabled(false);
        buttonSpendXP.addActionListener(e -> spendXP());
        buttons.add(buttonSpendXP);

        JButton button = new JButton(resourceMap.getString("close.text")); //$NON-NLS-1$
        button.addActionListener(e -> setVisible(false));
        buttons.add(button);

        panel.add(buttons);

        panel.setMaximumSize(new Dimension((int) panel.getPreferredSize().getWidth(), Short.MAX_VALUE));
        panel.setMinimumSize(new Dimension((int) panel.getPreferredSize().getWidth(), 300));

        return panel;
    }

    protected void updatePersonnelTable() {
        personnelSorter.sort();
        if(!choiceNoSkill.equals(choiceSkill.getSelectedItem())) {
            int rows = personnelTable.getRowCount();
            matchedPersonnelLabel.setText(String.format(resourceMap.getString("eligible.format"), rows)); //$NON-NLS-1$
        } else {
            matchedPersonnelLabel.setText(""); //$NON-NLS-1$
        }
    }

    protected void spendXP() {
        String skillName = (String) choiceSkill.getSelectedItem();
        if(choiceNoSkill.equals(skillName)) {
            // This shouldn't happen, but guard against it anyway.
            return;
        }
        int rows = personnelTable.getRowCount();
        int improvedPersonnelCount = rows;
        while(rows > 0) {
            for(int i = 0; i < rows; ++ i) {
                Person p = personnelModel.getPerson(personnelTable.convertRowIndexToModel(i));
                int cost;
                if(p.hasSkill(skillName)) {
                    cost = p.getCostToImprove(skillName);
                } else {
                    cost = SkillType.getType(skillName).getCost(0);
                }
                int startingExperienceLevel = p.getExperienceLevel(false);

                // Improve the skill and deduce the cost
                p.improveSkill(skillName);
                campaign.personUpdated(p);
                p.setXp(p.getXp() - cost);

                // The next part is bollocks and doesn't belong here, but as long as we hardcode AtB ...
                if (campaign.getCampaignOptions().getUseAtB()) {
                    if ((p.getPrimaryRole() > Person.T_NONE) && (p.getPrimaryRole() <= Person.T_CONV_PILOT)
                            && (p.getExperienceLevel(false) > startingExperienceLevel)
                            && (startingExperienceLevel >= SkillType.EXP_REGULAR)) {
                        SingleSpecialAbilityGenerator spaGenerator = new SingleSpecialAbilityGenerator();
                        String spa = spaGenerator.rollSPA(p);
                        if (null == spa) {
                            if(campaign.getCampaignOptions().useEdge()) {
                                p.getOptions().acquireAbility(PilotOptions.EDGE_ADVANTAGES, "edge", p.getEdge() + 1); //$NON-NLS-1$
                                PersonalLogger.gainedEdge(p, campaign.getDate());
                            }
                        } else {
                            PersonalLogger.gained(p, campaign.getDate(), spa);
                        }
                    }
                }
                MekHQ.triggerEvent(new PersonChangedEvent(p));
            }
            // Refresh the filter and continue if we still have anyone available
            updatePersonnelTable();
            rows = personnelTable.getRowCount();
            dataChanged = true;
        }
        if(improvedPersonnelCount > 0) {
            campaign.addReport(String.format(resourceMap.getString("improvedSkills.format"), skillName, improvedPersonnelCount)); //$NON-NLS-1$
        }

    }

    public boolean hasDataChanged() {
        return dataChanged;
    }

    public static class PersonnelFilter extends RowFilter<PersonnelTableModel, Integer> {
        private Integer primaryRole = null;
        private Integer expLevel = null;
        private String skill = null;
        private int maxSkillLevel = 10;
        private boolean prisoners = false;

        @Override
        public boolean include(RowFilter.Entry<? extends PersonnelTableModel, ? extends Integer> entry) {
            Person p = entry.getModel().getPerson(entry.getIdentifier().intValue());
            if (!p.isActive()) {
                return false;
            }
            if (!prisoners && !p.getPrisonerStatus().isFree()) {
                return false;
            }
            if ((null != primaryRole) && (p.getPrimaryRole() != primaryRole)) {
                return false;
            }
            if ((null != expLevel) && (p.getExperienceLevel(false) != expLevel)) {
                return false;
            }
            if ((null != skill)) {
                Skill s = p.getSkill(skill);
                if (null == s) {
                    int cost = SkillType.getType(skill).getCost(0);
                    return (cost >= 0) && (cost <= p.getXp());
                } else {
                    int cost = s.getCostToImprove();
                    return (s.getLevel() < maxSkillLevel) && (cost >= 0) && (cost <= p.getXp());
                }
            }
            return true;
        }

        public void setPrimaryRole(Integer role) {
            primaryRole = role;
        }

        public void setExpLevel(Integer level) {
            expLevel = level;
        }

        public void setSkill(String skillName) {
            skill = skillName;
        }

        public void setMaxSkillLevel(int level) {
            maxSkillLevel = level;
        }

        public void setAllowPrisoners(boolean allowPrisoners) {
            prisoners = allowPrisoners;
        }
    }

    private static class PersonTypeItem {
        public String name;
        public Integer id;

        public PersonTypeItem(String name, Integer id) {
            this.name = Objects.requireNonNull(name);
            this.id = id;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
