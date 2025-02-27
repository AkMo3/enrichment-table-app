package org.nrnb.gsoc.enrichment.utils;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.util.color.Palette;
import org.cytoscape.util.color.PaletteProvider;
import org.cytoscape.util.color.PaletteProviderManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;
import org.nrnb.gsoc.enrichment.model.ChartType;
import org.nrnb.gsoc.enrichment.model.EnrichmentTerm;
import org.nrnb.gsoc.enrichment.ui.EnrichmentTableModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ViewUtils {

    static String PIE_CHART = "piechart: attributelist=\"enrichmentTermsIntegers\" showlabels=\"false\" colorlist=\"";
    static String CIRCOS_CHART = "circoschart: firstarc=1.0 arcwidth=0.4 attributelist=\"enrichmentTermsIntegers\" showlabels=\"false\" colorlist=\"";
    static String CIRCOS_CHART2 = "circoschart: borderwidth=0 firstarc=1.0 arcwidth=0.4 attributelist=\"enrichmentTermsIntegers\" showlabels=\"false\" colorlist=\"";

    /**
     * Updates pie charts by changing or removing {@code NODE_CUSTOMGRAPHICS_4} component in style.
     *
     * @param registrar Service registrar to obtain required services
     * @param stringStyle Style which is required to be modified to update charts
     * @param show Boolean to turn on or off charts
     */
    public static void updatePieCharts(CyServiceRegistrar registrar, VisualStyle stringStyle, boolean show) {

        VisualMappingFunctionFactory passThroughFactory = registrar
                .getService(VisualMappingFunctionFactory.class, "(mapping.type=passthrough)");
        VisualLexicon lex = registrar.getService(RenderingEngineManager.class)
                .getDefaultVisualLexicon();
        // Set up the pass through mapping for the label
        if (show) {
            {
                VisualProperty<CyNode> customGraphics = (VisualProperty<CyNode>) lex.lookup(CyNode.class, "NODE_CUSTOMGRAPHICS_4");
                PassthroughMapping<String, CyNode> pMapping = (PassthroughMapping<String, CyNode>) passThroughFactory
                        .createVisualMappingFunction(EnrichmentTerm.colEnrichmentPassthrough, String.class,
                                customGraphics);
                stringStyle.addVisualMappingFunction(pMapping);
            }
        } else {
            stringStyle
                    .removeVisualMappingFunction(lex.lookup(CyNode.class, "NODE_CUSTOMGRAPHICS_4"));
        }
    }

    /**
     * Fetches colors  from {@link #getColorList(Map)} and updates color in
     * enrichment table and invokes {@link #updatePieCharts(CyServiceRegistrar, VisualStyle, boolean)}
     * to update current chart if chart present and saves information to network.
     * <p>
     * NOTE: Data saved in network table like string app
     * </p>
     * @param manager Application manager to access network related prorperties
     * @param registrar Service registrar to obtain required services
     */
    public static void drawCharts(CyApplicationManager manager, CyServiceRegistrar registrar,
                                  Map<EnrichmentTerm, String> selectedTerms,
                                  ChartType type) {
        CyNetwork network = manager.getCurrentNetwork();
        if (network == null || selectedTerms.size() == 0)
            return;

        CyTable nodeTable = network.getDefaultNodeTable();
        createColumns(nodeTable);

        List<String> colorList = getColorList(selectedTerms);
        List<String> shownTermNames = getTermNames(network, nodeTable, selectedTerms);

        for (CyNode node : network.getNodeList()) {
            List<Integer> nodeTermsIntegers =
                    nodeTable.getRow(node.getSUID()).getList(EnrichmentTerm.colEnrichmentTermsIntegers, Integer.class);
            String nodeColor = nodeColors(colorList, nodeTermsIntegers, type);
            nodeTable.getRow(node.getSUID()).set(EnrichmentTerm.colEnrichmentPassthrough, nodeColor);
            nodeTable.getRow(node.getSUID()).set(EnrichmentTerm.colEnrichmentTermsIntegers, nodeTermsIntegers);
        }

        // System.out.println(selectedTerms);
        VisualMappingManager vmm = registrar.getService(VisualMappingManager.class);
        CyNetworkView netView = manager.getCurrentNetworkView();
        if (netView != null) {
            ViewUtils.updatePieCharts(registrar, vmm.getVisualStyle(netView), true);
            netView.updateView();
        }
        // save in network table
        CyTable netTable = network.getDefaultNetworkTable();
        ModelUtils.createListColumnIfNeeded(netTable, String.class, ModelUtils.NET_ENRICHMENT_VISTEMRS);
        netTable.getRow(network.getSUID()).set(ModelUtils.NET_ENRICHMENT_VISTEMRS, shownTermNames);

        ModelUtils.createListColumnIfNeeded(netTable, String.class, ModelUtils.NET_ENRICHMENT_VISCOLORS);
        netTable.getRow(network.getSUID()).set(ModelUtils.NET_ENRICHMENT_VISCOLORS, colorList);
    }

    /**
     * Replaces column in table and removes colors from the enrichment table.
     *
     * @param manager Application manager to access network related properties
     * @param registrar Service registrar to obtain required services
     * @param model Enrichment Table model
     */
    public static void resetCharts(CyApplicationManager manager, CyServiceRegistrar registrar, EnrichmentTableModel model) {
        CyNetwork network = manager.getCurrentNetwork();
        if (network == null || model == null)
            return;

        CyTable nodeTable = network.getDefaultNodeTable();
        // replace columns
        ModelUtils.replaceListColumnIfNeeded(nodeTable, String.class,
                EnrichmentTerm.colEnrichmentTermsNames);
        ModelUtils.replaceListColumnIfNeeded(nodeTable, Integer.class,
                EnrichmentTerm.colEnrichmentTermsIntegers);
        ModelUtils.replaceColumnIfNeeded(nodeTable, String.class,
                EnrichmentTerm.colEnrichmentPassthrough);

        // remove colors from table?
        CyTable currTable = ModelUtils.getEnrichmentTable(registrar, network,
                EnrichmentTerm.TermSource.ALL.getTable());
        if (currTable == null || currTable.getRowCount() == 0) {
            return;
        }
        for (CyRow row : currTable.getAllRows()) {
            if (currTable.getColumn(EnrichmentTerm.colChartColor) != null
                    && row.get(EnrichmentTerm.colChartColor, String.class) != null
                    && !row.get(EnrichmentTerm.colChartColor, String.class).equals("")) {
                row.set(EnrichmentTerm.colChartColor, "");
            }
        }
        // initPanel();
        model.fireTableDataChanged();
    }

    /**
     * Provides default palette from {@link #getDefaultPalette(CyServiceRegistrar)} if palette not already
     * present in session.
     *
     * @param network CyNetwork currently in use.
     * @param table Current Enrichment Table,
     * @param registrar Service registrar to obtain required services
     * @return A {@link Palette} as per session state.
     */
    public static Palette getEnrichmentPalette(CyNetwork network, CyTable table, CyServiceRegistrar registrar) {
        Palette palette = SessionUtils.getEnrichmentPalette(network, table);
        if (Objects.isNull(palette)) return getDefaultPalette(registrar);
        return palette;
    }

    private static void createColumns(CyTable nodeTable) {
        // replace columns
        ModelUtils.replaceListColumnIfNeeded(nodeTable, String.class,
                EnrichmentTerm.colEnrichmentTermsNames);
        ModelUtils.replaceListColumnIfNeeded(nodeTable, Integer.class,
                EnrichmentTerm.colEnrichmentTermsIntegers);
        ModelUtils.replaceColumnIfNeeded(nodeTable, String.class,
                EnrichmentTerm.colEnrichmentPassthrough);
    }

    private static List<String> getColorList(Map<EnrichmentTerm, String> selectedTerms) {
        List<String> colorList = new ArrayList<>();
        for (EnrichmentTerm term : selectedTerms.keySet()) {
            String color = selectedTerms.get(term);
            if (color != null) {
                colorList.add(color);
            } else {
                colorList.add("");
            }
        }
        return colorList;
    }

    private static List<String> getTermNames(CyNetwork network, CyTable nodeTable,
                                             Map<EnrichmentTerm, String> selectedTerms) {
        List<String> shownTermNames = new ArrayList<>();
        boolean firstTerm = true;
        for (EnrichmentTerm term : selectedTerms.keySet()) {
            String selTerm = term.getName();
            shownTermNames.add(selTerm);
            List<Long> enrichedNodeSUIDs = term.getNodesSUID();
            for (CyNode node : network.getNodeList()) {
                List<Integer> nodeTermsIntegers = nodeTable.getRow(node.getSUID())
                        .getList(EnrichmentTerm.colEnrichmentTermsIntegers, Integer.class);
                List<String> nodeTermsNames = nodeTable.getRow(node.getSUID())
                        .getList(EnrichmentTerm.colEnrichmentTermsNames, String.class);
                if (firstTerm || nodeTermsIntegers == null)
                    nodeTermsIntegers = new ArrayList<Integer>();
                if (firstTerm || nodeTermsNames == null) {
                    nodeTermsNames = new ArrayList<String>();
                }
                if (enrichedNodeSUIDs.contains(node.getSUID())) {
                    nodeTermsNames.add(selTerm);
                    nodeTermsIntegers.add(1);
                } else {
                    nodeTermsNames.add("");
                    nodeTermsIntegers.add(0);
                }
                nodeTable.getRow(node.getSUID()).set(EnrichmentTerm.colEnrichmentTermsIntegers, nodeTermsIntegers);
                nodeTable.getRow(node.getSUID()).set(EnrichmentTerm.colEnrichmentTermsNames, nodeTermsNames);
            }
            if (firstTerm) firstTerm = false;
        }
        return shownTermNames;
    }

    private static String nodeColors(List<String> colors, List<Integer> nodeTermFlags, ChartType type) {
        boolean foundTerm = false;
        for (Integer term : nodeTermFlags) {
            if (term > 0) {
                foundTerm = true;
                break;
            }
        }
        if (!foundTerm) return null;

        StringBuilder colorString = new StringBuilder();
        if (type.equals(ChartType.FULL) || type.equals(ChartType.PIE)) {
            for (String color : colors) {
                colorString.append(color).append(",");
            }
        } else {
            for (int i = 0; i < colors.size(); i++) {
                if (nodeTermFlags.get(i) > 0) {
                    if (type.equals(ChartType.TEETH))
                        colorString.append(colors.get(i)).append("ff,");
                    else
                        colorString.append(colors.get(i)).append(",");
                } else {
                    if (type.equals(ChartType.TEETH))
                        colorString.append("#ffffff00,");
                    else
                        colorString.append("#ffffff,");
                    nodeTermFlags.set(i, 1);
                }
            }
        }
        if (type.equals(ChartType.PIE) || type.equals(ChartType.SPLIT_PIE))
            return PIE_CHART + colorString.substring(0, colorString.length() - 1) + "\"";
        if (type.equals(ChartType.TEETH))
            return CIRCOS_CHART2 + colorString.substring(0, colorString.length() - 1) + "\"";
        return CIRCOS_CHART + colorString.substring(0, colorString.length() - 1) + "\"";
    }

    private static Palette getDefaultPalette(CyServiceRegistrar registrar) {
        PaletteProviderManager pm = registrar.getService(PaletteProviderManager.class);
        PaletteProvider brewerProvider = pm.getPaletteProvider("ColorBrewer");
        return brewerProvider.getPalette("Paired colors");
    }
}
