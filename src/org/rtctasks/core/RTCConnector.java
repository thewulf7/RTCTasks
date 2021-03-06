package org.rtctasks.core;

import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.common.IExtensibleItem;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.transport.TeamServiceException;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.common.IQueryCommon;
import com.ibm.team.workitem.common.expression.*;
import com.ibm.team.workitem.common.model.AttributeOperation;
import com.ibm.team.workitem.common.model.IAttributeHandle;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.ItemProfile;
import com.ibm.team.workitem.common.query.IQueryResult;
import com.ibm.team.workitem.common.query.IResolvedResult;
import com.ibm.team.workitem.common.query.ResultSize;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.rtctasks.RTCTask;

import java.nio.channels.ClosedByInterruptException;
import java.util.*;

/**
 * Created by exm1110B.
 * Date: 17/07/2015, 15:14
 */
public class RTCConnector {

    private static final Map<String, RTCConnector> _conPool = new HashMap<String, RTCConnector>();
    private final IWorkItemClient _workItemClient;
    private final IProjectArea _projectArea;
    private IQueryableAttributeFactory _factory;


    public static void main(String[] args) throws TeamRepositoryException {
        final String url = args[0];
        final String user =  args[1];
        final String pass = args[2];
        final String projectArea = args[3];
        final RTCConnector connector = getConnector(url, user, pass, projectArea);
        //        final IWorkItem workItemBy = connector.getWorkItemBy(31297);
        //        System.out.println(workItemBy.getHTMLSummary());
        //        final RTCTask rtcTask1 = new RTCTask(workItemBy);
        //        final Comment[] comments = rtcTask1.getComments();
        //        for (Comment comment : comments) {
        //
        //            System.out.println(comment.getText());
        //        }
        final Collection<IWorkItem> jms = connector.getWorkItemsBy("JMS");
        for (IWorkItem jm : jms) {

            final List<IAttributeHandle> customAttributes = jm.getCustomAttributes();
            for (IAttributeHandle customAttribute : customAttributes) {

            }
            final RTCTask x = new RTCTask(jm);

            System.out.println(x.getDescription());
        }
        close();
    }

    static {
        TeamPlatform.startup();
    }

    static void close() {
        TeamPlatform.shutdown();
    }

    public static RTCConnector getConnector(String url, String user, String pass, String projectArea) throws TeamRepositoryException {
        final RTCConnector rtcConnector;
        if (!_conPool.containsKey(url + user + pass + projectArea)) {
            rtcConnector = new RTCConnector(url, user, pass, projectArea);
        } else {
            rtcConnector = _conPool.get(url + user + pass + projectArea);
        }
        return rtcConnector;
    }

    private final ITeamRepository _repository;
    private final IProgressMonitor monitor = new NullProgressMonitor();
    private final IProcessItemService connect;

    public RTCConnector(final String url, final String username, final String password, String projectArea) throws TeamRepositoryException {
        _repository = TeamPlatform.getTeamRepositoryService().getTeamRepository(url);
        _repository.registerLoginHandler(new ITeamRepository.ILoginHandler() {
            public ILoginInfo challenge(ITeamRepository repository) {
                return new ILoginInfo() {
                    public String getUserId() {
                        return username;
                    }

                    public String getPassword() {
                        return password;
                    }
                };
            }
        });
        _repository.login(monitor);
        connect = (IProcessItemService) _repository.getClientLibrary(IProcessItemService.class);
        _workItemClient = (IWorkItemClient) _repository.getClientLibrary(IWorkItemClient.class);
        _factory = QueryableAttributes.getFactory(IWorkItem.ITEM_TYPE);
        _projectArea = getProjectArea(projectArea);
        //   final List<IQueryableAttribute> allAttributes = _factory.findAllAttributes(_projectArea, _workItemClient.getAuditableCommon(), monitor);
        //        for (IQueryableAttribute allAttribute : allAttributes) {
        //            final String displayName = allAttribute.getDisplayName();
        //            System.out.println(displayName+" "+allAttribute.getIdentifier());
        //        }
    }

    public IProgressMonitor getMonitor() {
        return monitor;
    }

    private IProjectArea getProjectArea(String name) throws TeamRepositoryException {
        final List<IProjectArea> allProjectAreas = connect.findAllProjectAreas(null, monitor);//TODO: wrap in an exception and get root cause, try again if InterruptedException
        for (IProjectArea projectArea : allProjectAreas) {
            if (projectArea.getName().equals(name)) {
                return projectArea;
            }
        }
        throw new RuntimeException("Project Area " + _projectArea + " Not found");
    }

    public List<IWorkItem> getWorkItemsBy(String value) throws TeamRepositoryException {


        //        final IQueryableAttribute idAttribute = findAttribute(_projectArea, IWorkItem.ID_PROPERTY, monitor);
        //        final AttributeExpression idExpression = new AttributeExpression(idAttribute, AttributeOperation.EQUALS,Integer.parseInt(value));

        final IQueryableAttribute summeryAttribute = findAttribute(_projectArea, IWorkItem.SUMMARY_PROPERTY, monitor);
        final AttributeExpression summeryExpression = new AttributeExpression(summeryAttribute, AttributeOperation.CONTAINS, value);

        final IQueryableAttribute descriptionAttribute = findAttribute(_projectArea, IWorkItem.DESCRIPTION_PROPERTY, monitor);
        final AttributeExpression descriptionExpression = new AttributeExpression(descriptionAttribute, AttributeOperation.CONTAINS, value);

        final IQueryableAttribute projectAreaAttribute = findAttribute(_projectArea, IWorkItem.PROJECT_AREA_PROPERTY, monitor);
        final AttributeExpression projectAreaExpression = new AttributeExpression(projectAreaAttribute, AttributeOperation.EQUALS, _projectArea);

        //        final Term term = new Term(Term.Operator.AND);
        //        term.add(projectAreaExpression);
        //        term.add(summeryExpression);


        final Term term = new Term(Term.Operator.AND);
        if (value != null && !value.isEmpty()) {
            final Term termOr = new Term(Term.Operator.OR);
            //   termOr.add(idExpression);
            termOr.add(summeryExpression);
            //    termOr.add(descriptionExpression);
            term.add(termOr);
        }

        term.add(projectAreaExpression);

        final IQueryCommon queryService = _workItemClient.getQueryClient();
        final ItemProfile<IWorkItem> profile = IWorkItem.FULL_PROFILE;
        final IQueryResult<IResolvedResult<IWorkItem>> result = queryService.getResolvedExpressionResults(_projectArea, term, profile);

        List<IWorkItem> matchingWorkItems = new ArrayList<IWorkItem>(0);
        try {
            final ResultSize resultSize = result.getResultSize(monitor);
            final int totalAvailable = resultSize.getTotalAvailable();
            matchingWorkItems=new ArrayList<IWorkItem>(totalAvailable);
            while (result.hasNext(monitor)) {
                final IWorkItem item = result.next(monitor).getItem();
                matchingWorkItems.add(item);
            }
        } catch (TeamServiceException e) {
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof ClosedByInterruptException) {
                System.out.println("Stopped query");
            } else {
                throw e;
            }

        }
        return matchingWorkItems;
    }

    public IWorkItem getWorkItemBy(int id) throws TeamRepositoryException {
        try {
            final IWorkItem workItemById = _workItemClient.findWorkItemById(id, IWorkItem.FULL_PROFILE, monitor);
            return workItemById;
        } catch (TeamServiceException e) {
            final Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof ClosedByInterruptException) {
                System.out.println("Stopped query");
                return null;
            } else {
                throw e;
            }
        }

    }

    public void processResolvedResults(IQueryResult<IResolvedResult> resolvedResults) throws TeamRepositoryException {
        // Get the required client libraries
        long processed = 0;
        while (resolvedResults.hasNext(monitor)) {
            final IResolvedResult result = resolvedResults.next(monitor);
            final IWorkItem workItem = (IWorkItem) result.getItem();
            System.out.println(workItem.getHTMLSummary());
            // do something with the work item
            processed++;
        }
        System.out.println("Processedlts: " + processed);
    }

    private IQueryableAttribute findAttribute(IProjectAreaHandle projectArea, String attributeId, IProgressMonitor monitor) throws TeamRepositoryException {
        return _factory.findAttribute(projectArea, attributeId, _workItemClient.getQueryClient().getAuditableCommon(), monitor);
    }

    private ItemProfile<IWorkItem> getProfile(IQueryableAttribute attribute) {
        if (!attribute.isStateExtension()) return IWorkItem.SMALL_PROFILE.createExtension(attribute.getIdentifier());
        return IWorkItem.SMALL_PROFILE.createExtension(IWorkItem.CUSTOM_ATTRIBUTES_PROPERTY, IExtensibleItem.TIMESTAMP_EXTENSIONS_QUERY_PROPERTY);
    }


}


