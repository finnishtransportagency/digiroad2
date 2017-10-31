(function(root) {
  root.ProjectCollection = function(backend) {
    var roadAddressProjects = [];
    var currentRoadPartList = [];
    var reservedDirtyRoadPartList = [];
    var dirtyRoadPartList = [];
    var projectinfo;
    var currentProject;
    var fetchedProjectLinks = [];
    var fetchedSuravageProjectLinks = [];
    var roadAddressProjectLinks = [];
    var dirtyProjectLinkIds = [];
    var dirtyProjectLinks = [];
    var self = this;
    var publishableProject = false;
    var LinkStatus = LinkValues.LinkStatus;
    var ProjectStatus = LinkValues.ProjectStatus;
    var LinkGeomSource = LinkValues.LinkGeomSource;
    var BAD_REQUEST_400 = 400;
    var UNAUTHORIZED_401 = 401;
    var PRECONDITION_FAILED_412 = 412;
    var INTERNAL_SERVER_ERROR_500 = 500;

    var projectLinks = function() {
      return _.flatten(fetchedProjectLinks);
    };

    var projectSuravageLinks = function () {
      return _.flatten(fetchedSuravageProjectLinks);
    };

    this.getProjectLinks = function() {
      return _.flatten(fetchedProjectLinks);
    };

    this.getSuravageProjectLinks = function(){
      return _.flatten(fetchedSuravageProjectLinks);
    };

    this.getAll = function () {
      return _.map(projectLinks(), function(projectLink) {
        return projectLink.getData();
      });
    };

    this.getLinkStatus = function () {
      return LinkStatus;
    };

    this.reset = function(){
      fetchedProjectLinks = [];
    };

    this.resetSuravage = function () {
      fetchedSuravageProjectLinks = [];
    };

    this.getMultiSelectIds = function (linkId) {
      var chain = _.find(fetchedProjectLinks, function (linkChain) {
        var pureChain = _.map(linkChain, function(l) { return l.getData(); });
        return _.some(pureChain, {"linkId": linkId});
      });
      return _.map(chain, function (link) { return link.getData().linkId; });
    };

    this.getByLinkId = function (ids) {
      var links = _.filter(_.flatten(fetchedProjectLinks), function (projectLink){
        return _.contains(ids, projectLink.getData().linkId);
      });
      return links;
    };

    this.fetch = function(boundingBox, zoom, projectId, isPublishable) {
      var id = projectId;
      if (typeof id === 'undefined' && typeof projectinfo !== 'undefined')
        id = projectinfo.id;
      if (id)
        backend.getProjectLinks({boundingBox: boundingBox, zoom: zoom, projectId: id}, function(fetchedLinks) {
          fetchedProjectLinks = _.map(fetchedLinks, function(projectLinkGroup) {
            return _.map(projectLinkGroup, function(projectLink) {
              return new ProjectLinkModel(projectLink);
            });
          });
          publishableProject = isPublishable;

          var separated = _.partition(self.getAll(), function(projectRoad){
            return projectRoad.roadLinkSource === LinkGeomSource.SuravageLinkInterface.value;
          });
          fetchedSuravageProjectLinks = separated[0];
          var nonSuravageProjectRoads = separated[1];
          eventbus.trigger('roadAddressProject:fetched', nonSuravageProjectRoads);
          if(fetchedSuravageProjectLinks.length !== 0){
            eventbus.trigger('suravageroadAddressProject:fetched',fetchedSuravageProjectLinks);
          }
        });
    };

    this.getProjects = function () {
      return backend.getRoadAddressProjects(function (projects) {
        roadAddressProjects = projects;
        eventbus.trigger('roadAddressProjects:fetched', projects);
      });
    };

    this.getProjectsWithLinksById = function (projectId, openForm) {
      return backend.getProjectsWithLinksById(projectId, function (result) {
        roadAddressProjects = result.project;
        currentProject = result;
        projectinfo = {
          id: result.project.id,
          publishable: result.publishable
        };
        publishableProject = result.publishable;
        eventbus.trigger('roadAddressProject:projectFetched', projectinfo);
        if(openForm){
          eventbus.trigger('roadAddress:openProject', result);
          if(applicationModel.isReadOnly()) {
            $('.edit-mode-btn:visible').click();
          }
        }
      });
    };

    this.revertLinkStatus = function () {
      var fetchedLinks = this.getAll();
      dirtyProjectLinkIds.forEach(function (dirtyLink) {
        _.filter(fetchedLinks, {linkId: dirtyLink.id}).forEach(function (fetchedLink) {
          fetchedLink.status = dirtyLink.status;
        });
      });
    };

    this.clearRoadAddressProjects = function () {
      roadAddressProjects = [];
      dirtyRoadPartList = [];
      currentRoadPartList = [];
      dirtyProjectLinkIds = [];
      dirtyProjectLinks = [];
      reservedDirtyRoadPartList = [];
      projectinfo=undefined;
      backend.abortLoadingProject();
    };

    this.saveProject = function (data) {
      var projectid = 0;
      if (projectinfo !== undefined) {
        projectid = projectinfo.id;
      } else if (currentProject!==undefined && currentProject.project.id!==undefined)
      {
        projectid=currentProject.project.id;
      }
      var dataJson = {
        id: projectid,
        projectEly: currentProject.project.ely,
        status: currentProject.project.statusCode,
        name: data[0].value,
        startDate: data[1].value,
        additionalInfo: data[2].value,
        roadPartList: _.map(dirtyRoadPartList.concat(reservedDirtyRoadPartList), function(part){
          return {discontinuity: part.discontinuity,
            ely: part.ely,
            roadLength: part.roadLength,
            roadNumber: part.roadNumber,
            roadPartId: 0,
            roadPartNumber: part.roadPartNumber,
            startingLinkId: part.startingLinkId
          };
        })
      };

      backend.saveRoadAddressProject(dataJson, function (result) {
        if (result.success) {
          projectinfo = {
            id: result.project.id,
            additionalInfo: result.project.additionalInfo,
            status: result.project.status,
            startDate: result.project.startDate,
            publishable: false
          };
          eventbus.trigger('roadAddress:projectSaved', result);
          dirtyRoadPartList = result.formInfo;
          currentProject = result;
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result.errorMessage);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    this.revertChangesRoadlink = function (links) {
      if(!_.isEmpty(links)) {
        applicationModel.addSpinner();
        var data = {
          'projectId': currentProject.project.id,
          'roadNumber': links[0].roadNumber,
          'roadPartNumber': links[0].roadPartNumber,
          'links': _.map(links, function (link) {
            return {'id': link.id, 'linkId': link.linkId, 'status': link.status};
          })
        };
        backend.revertChangesRoadlink(data, function (response) {
          if (response.success) {
            dirtyProjectLinkIds = [];
            eventbus.trigger('projectLink:revertedChanges');
          }
          else if (response.status == INTERNAL_SERVER_ERROR_500 || response.status == BAD_REQUEST_400) {
            eventbus.trigger('roadAddress:projectLinksUpdateFailed', error.status);
            new ModalConfirm(response.errorMessage);
            applicationModel.removeSpinner();
          }
          else{
            new ModalConfirm(response.errorMessage);
            applicationModel.removeSpinner();
          }
        });
      }
    };

    this.removeProjectLinkSplit = function (project, selectedProjectLink) {
      if(!_.isEmpty(project)) {
        applicationModel.addSpinner();
        var projectId = project.id;
        var linkId = Math.abs(selectedProjectLink[0].linkId);
        backend.removeProjectLinkSplit(projectId, linkId, function (response) {
          if (response.success) {
            dirtyProjectLinkIds = [];
            eventbus.trigger('projectLink:revertedChanges');
          }
          else if (response == INTERNAL_SERVER_ERROR_500 || response == BAD_REQUEST_400) {
            eventbus.trigger('roadAddress:projectLinksUpdateFailed', error.status);
            new ModalConfirm(response.message);
            applicationModel.removeSpinner();
          }
          else{
            new ModalConfirm(response.message);
            applicationModel.removeSpinner();
          }
        });
      }
    };

    this.saveProjectLinks = function(changedLinks, statusCode) {
      applicationModel.addSpinner();
      var linkIds = _.unique(_.map(changedLinks,function (t){
        if(!_.isUndefined(t.linkId)){
          return t.linkId;
        } else return t;
      }));

      var projectId = projectinfo.id;

      var dataJson = {
        linkIds: linkIds,
        linkStatus: statusCode,
        projectId: projectId,
        roadNumber: Number($('#roadAddressProjectForm').find('#tie')[0].value),
        roadPartNumber: Number($('#roadAddressProjectForm').find('#osa')[0].value),
        trackCode: Number($('#roadAddressProjectForm').find('#ajr')[0].value),
        discontinuity: Number($('#roadAddressProjectForm').find('#discontinuityDropdown')[0].value),
        roadEly: Number($('#roadAddressProjectForm').find('#ely')[0].value),
        roadLinkSource: Number(_.first(changedLinks).roadLinkSource),
        roadType: Number($('#roadAddressProjectForm').find('#roadTypeDropDown')[0].value),
        userDefinedEndAddressM: null
      };

      var endDistance = parseInt($('#endDistance').val());
      var originalEndDistance = _.chain(changedLinks).uniq().sortBy(function(cl){
        return cl.endAddressM;
      }).last().value().endAddressM;
      if(!isNaN(endDistance) && !isNaN(originalEndDistance) && originalEndDistance !== endDistance){
        dataJson.userDefinedEndAddressM = endDistance;
      }
      if(!_.isEmpty(linkIds) && typeof projectId !== 'undefined' && projectId !== 0){
        var ids = _.chain(changedLinks).map(function (cl) {
          return cl.id;
        }).uniq().value();
        if(statusCode == LinkStatus.New.value && ids.length === 1 && ids[0] === 0){
          backend.createProjectLinks(dataJson, function(successObject) {
            if (!successObject.success) {
              new ModalConfirm(successObject.errormessage);
              applicationModel.removeSpinner();
            } else {
              publishableProject = successObject.publishable;
              eventbus.trigger('projectLink:projectLinksCreateSuccess');
              eventbus.trigger('roadAddress:projectLinksCreateSuccess');
            }
          });
        }
        else {
          backend.updateProjectLinks(dataJson, function (successObject) {
            if (!successObject.success) {
              new ModalConfirm(successObject.errormessage);
              applicationModel.removeSpinner();
            } else {
              publishableProject = successObject.publishable;
              eventbus.trigger('roadAddress:projectLinksUpdated', successObject);
            }
          });
        }
      } else {
        eventbus.trigger('roadAddress:projectLinksUpdateFailed', PRECONDITION_FAILED_412);
      }
    };

    this.saveCuttedProjectLinks = function(changedLinks, statusCodeA, statusCodeB){
      applicationModel.addSpinner();
      
      var linkId = Math.abs(changedLinks[0].linkId);

      var projectId = projectinfo.id;
      var form = $('#roadAddressProjectFormCut');

      var dataJson = {
        splitPoint: {
          x: Number(form.find('#splitx')[0].value),
          y: Number(form.find('#splity')[0].value)
        },
        statusA: statusCodeA,
        statusB: statusCodeB,
        roadNumber: Number(form.find('#tie')[0].value),
        roadPartNumber: Number(form.find('#osa')[0].value),
        trackCode: Number(form.find('#ajr')[0].value),
        discontinuity: Number(form.find('#discontinuityDropdown')[0].value),
        ely: Number(form.find('#ely')[0].value),
        roadLinkSource: Number(_.first(changedLinks).roadLinkSource),
        roadType: Number(form.find('#roadTypeDropDown')[0].value),
        projectId: projectId
      };

      backend.saveProjectLinkSplit(dataJson, linkId, function(successObject){
        if (!successObject.success) {
          new ModalConfirm(successObject.reason);
          applicationModel.removeSpinner();
        }
        else{
          eventbus.trigger('roadAddress:projectLinksUpdated', successObject);
      }}, function(failureObject){
          new ModalConfirm(failureObject.reason);
          applicationModel.removeSpinner();
      });
    };

    this.createProject = function (data) {

      var dataJson = {
        id: 0,
        status: 1,
        name: data[0].value,
        startDate: data[1].value,
        additionalInfo: data[2].value,
        roadPartList: dirtyRoadPartList
      };

      backend.createRoadAddressProject(dataJson, function (result) {
        if (result.success) {
          projectinfo = {
            id: result.project.id,
            additionalInfo: result.project.additionalInfo,
            status: result.project.status,
            startDate: result.project.startDate,
            publishable: false
          };
          eventbus.trigger('roadAddress:projectSaved', result);
          dirtyRoadPartList = result.formInfo;
          currentProject = result;
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result.errorMessage);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    this.changeNewProjectLinkDirection = function (projectId, selectedLinks){
      applicationModel.addSpinner();
      var data = [projectId, selectedLinks[0].roadNumber, selectedLinks[0].roadPartNumber] ;
      backend.directionChangeNewRoadlink(data, function(successObject) {
        if (!successObject.success) {
          eventbus.trigger('roadAddress:changeDirectionFailed', successObject.errorMessage);
          applicationModel.removeSpinner();
        } else {
          eventbus.trigger('changeProjectDirection:clicked');
        }
      });
    };

    this.publishProject = function() {
      backend.sendProjectToTR(projectinfo.id, function(result) {
        if(result.sendSuccess) {
          eventbus.trigger('roadAddress:projectSentSuccess');
        }
        else {
          eventbus.trigger('roadAddress:projectSentFailed', result.errorMessage);
        }
      }, function(result) {
        eventbus.trigger('roadAddress:projectSentFailed', result.status);
      });
    };

    var addSmallLabel = function (label) {
      return '<label class="control-label-small">' + label + '</label>';
    };

    var addSmallLabelWithIds = function(label, id){
      return '<label class="control-label-small" id='+ id+'>'+label+'</label>';
    };

    var updateFormInfo = function (formInfo) {
      $("#roadpartList").append($("#roadpartList").html(formInfo));
      $("#newReservedRoads").append($("#newReservedRoads").html(formInfo));
    };

    var parseRoadPartInfoToResultRow = function () {
      var listContent = '';
      var index = 0;
      _.each(reservedDirtyRoadPartList, function (row) {
          var button = deleteButton(index++, row.roadNumber, row.roadPartNumber);
          listContent += '<div style="display:inline-block;">'+ button+ addSmallLabelWithIds(row.roadNumber,'reservedRoadNumber') + addSmallLabelWithIds(row.roadPartNumber, 'reservedRoadPartNumber') + addSmallLabelWithIds(row.roadLength, 'reservedRoadLength') + addSmallLabelWithIds(row.discontinuity, 'reservedDiscontinuity') + addSmallLabelWithIds(row.ely, 'reservedEly') +'</div>';
        }
      );
      return listContent;
    };

    this.getDeleteButton = function (index, roadNumber, roadPartNumber) {
      return deleteButton(index, roadNumber, roadPartNumber);
    };

    var deleteButton = function(index, roadNumber, roadPartNumber){
      var disabledInput = !_.isUndefined(currentProject) && currentProject.project.statusCode === ProjectStatus.ErroredInTR.value;
      return '<button roadNumber="'+roadNumber+'" roadPartNumber="'+roadPartNumber+'" id="'+index+'" class="delete btn-delete" '+ (disabledInput ? 'disabled' : '') +'>X</button>';
    };

    var addToDirtyRoadPartList = function (queryresult) {
      var qRoadparts = [];
      _.each(queryresult.roadparts, function (row) {
        qRoadparts.push(row);
      });

      var sameElements = arrayIntersection(qRoadparts, reservedDirtyRoadPartList, function (arrayarow, arraybrow) {
        return arrayarow.roadPartId === arraybrow.roadPartId;
      });
      _.each(sameElements, function (row) {
        _.remove(qRoadparts, row);
      });
      _.each(qRoadparts, function (row) {
        reservedDirtyRoadPartList.push(row);
      });
    };

    this.deleteRoadPartFromList = function(list, roadNumber, roadPartNumber){
      return _.filter(list,function (dirty) {
        return !(dirty.roadNumber.toString() === roadNumber && dirty.roadPartNumber.toString() === roadPartNumber);
      });
    };

    this.setDirty = function(editedRoadLinks) {
      dirtyProjectLinkIds = editedRoadLinks;
      eventbus.trigger('roadAddress:projectLinksEdited');
    };

    this.getDirty = function() {
      return dirtyProjectLinkIds;
    };

    this.getDirtyRoadParts = function () {
      return dirtyRoadPartList;
    };

    this.setDirtyRoadParts = function (list) {
      dirtyRoadPartList = list;
    };

    this.getReservedDirtyRoadParts = function () {
      return reservedDirtyRoadPartList;
    };

    this.setReservedDirtyRoadParts = function (list) {
      reservedDirtyRoadPartList = list;
    };

    this.getCurrentRoadPartList = function(){
      return currentRoadPartList;
    };

    this.setTmpDirty = function(editRoadLinks){
      dirtyProjectLinks = editRoadLinks;
    };

    this.getTmpDirty = function(){
      return dirtyProjectLinks;
    };

    this.setCurrentRoadPartList = function(parts){
      if(!_.isUndefined(parts)) {
        currentRoadPartList = parts.slice(0);
        dirtyRoadPartList = parts.slice(0);
      }
    };

    this.isDirty = function() {
      return dirtyProjectLinks.length > 0;
    };

    this.roadIsOther = function(road){
      return  0 === road.roadNumber && 0 === road.anomaly && 0 === road.roadLinkType && 0 === road.roadPartNumber && 99 === road.trackCode;
    };

    this.roadIsUnknown = function(road){
      return  0 === road.roadNumber && 1 === road.anomaly && 0 === road.roadLinkType && 0 === road.roadPartNumber && 99 === road.trackCode;
    };

    function arrayIntersection(a, b, areEqualFunction) {
      return _.filter(a, function(aElem) {
        return _.any(b, function(bElem) {
          return areEqualFunction(aElem,bElem);
        });
      });
    }

    eventbus.on('roadAddressProject:startProject', this.getProjectsWithLinksById);

    eventbus.on('roadPartsValidation:checkRoadParts', function(validationResult) {
      if (validationResult.success !== "ok") {
        eventbus.trigger('roadAddress:projectValidationFailed', validationResult.success);
      } else {
        addToDirtyRoadPartList(validationResult);
        updateFormInfo(parseRoadPartInfoToResultRow());
        eventbus.trigger('roadAddress:projectValidationSucceed');
      }
    });

    eventbus.on('clearproject', function() {
      this.clearRoadAddressProjects();
    });

    this.getCurrentProject = function(){
      return currentProject;
    };

    this.getPublishableStatus = function () {
      return publishableProject;
    };

    this.checkIfReserved = function (data) {
      return backend.checkIfRoadpartReserved(data[3].value === '' ? 0 : parseInt(data[3].value), data[4].value === '' ? 0 : parseInt(data[4].value), data[5].value === '' ? 0 : parseInt(data[5].value), data[1].value);

    };

    var ProjectLinkModel = function(data) {

      var getData = function() {
        return data;
      };

      return {
        getData: getData
      };
    };

    this.reOpenProjectById = function(projectId){
      backend.reOpenProject(projectId, function(successObject) {
        eventbus.trigger("roadAddressProject:reOpenedProject",successObject);
      }, function(errorObject){
        if(!_.isUndefined(errorObject.message)) {
          new ModalConfirm(errorObject.message.toString());
        } else{
          new ModalConfirm(errorObject.statusText.toString());
        }
        applicationModel.removeSpinner();
        console.log("Error at deleting rotatingId: " + errorObject);
      });
    };
  };
})(this);
