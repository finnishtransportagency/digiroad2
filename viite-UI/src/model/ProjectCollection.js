(function(root) {
  root.ProjectCollection = function(backend) {
    var roadAddressProjects = [];
    var projectErrors = [];
    var currentReservedParts = [];
    var coordinateButtons = [];
    var newReservedParts = [];
    var projectInfo;
    var currentProject;
    var fetchedProjectLinks = [];
    var fetchedSuravageProjectLinks = [];
    var dirtyProjectLinkIds = [];
    var dirtyProjectLinks = [];
    var self = this;
    var publishableProject = false;
    var LinkStatus = LinkValues.LinkStatus;
    var ProjectStatus = LinkValues.ProjectStatus;
    var LinkGeomSource = LinkValues.LinkGeomSource;
    var Track = LinkValues.Track;
    var BAD_REQUEST_400 = 400;
    var UNAUTHORIZED_401 = 401;
    var PRECONDITION_FAILED_412 = 412;
    var INTERNAL_SERVER_ERROR_500 = 500;

    var projectLinks = function() {
      return _.flatten(fetchedProjectLinks);
    };

    this.getProjectLinks = function() {
      return _.flatten(fetchedProjectLinks);
    };

    this.getAll = function () {
      return _.map(projectLinks(), function(projectLink) {
        return projectLink.getData();
      });
    };

    this.reset = function(){
      fetchedProjectLinks = [];
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
      if (typeof id === 'undefined' && typeof projectInfo !== 'undefined')
        id = projectInfo.id;
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

    this.getProjectsWithLinksById = function (projectId) {
      return backend.getProjectsWithLinksById(projectId, function (result) {
        roadAddressProjects = result.project;
        currentProject = result;
        projectInfo = {
          id: result.project.id,
          publishable: result.publishable
        };
        projectErrors = result.projectErrors;
        publishableProject = result.publishable;
        eventbus.trigger('roadAddressProject:projectFetched', projectInfo);
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
      currentReservedParts = [];
      newReservedParts = [];
      dirtyProjectLinkIds = [];
      dirtyProjectLinks = [];
      projectInfo=undefined;
      backend.abortLoadingProject();
    };

    this.saveProject = function (data,resolution) {
      var projectId = 0;
      if (projectInfo !== undefined) {
        projectId = projectInfo.id;
      } else if (currentProject!==undefined && currentProject.project.id!==undefined)
      {
        projectId=currentProject.project.id;
      }
      var dataJson = {
        id: projectId,
        projectEly: currentProject.project.ely,
        status: currentProject.project.statusCode,
        name: data[0].value,
        startDate: data[1].value,
        additionalInfo: data[2].value,
        roadPartList: _.map(self.getAllReservedParts(), function(part){
          return {
            discontinuity: (part.newDiscontinuity ? part.newDiscontinuity: part.currentDiscontinuity),
            ely: (part.newEly ? part.newEly: part.currentEly),
            roadLength: (part.newLength ? part.newLength: part.currentLength),
            roadNumber: part.roadNumber,
            roadPartId: 0,
            roadPartNumber: part.roadPartNumber,
            startingLinkId: part.startingLinkId
          };
        }),
        resolution: resolution
      };

      backend.saveRoadAddressProject(dataJson, function (result) {
        if (result.success) {
          projectInfo = {
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
        var coordinates = applicationModel.getUserGeoLocation();
        var data = {
          'projectId': currentProject.project.id,
          'roadNumber': links[0].roadNumber,
          'roadPartNumber': links[0].roadPartNumber,
          'links': _.map(links, function (link) {
            return {'id': link.id, 'linkId': link.linkId, 'status': link.status};
          }),
          'coordinates': coordinates
        };
        backend.revertChangesRoadlink(data, function (response) {
          if (response.success) {
            dirtyProjectLinkIds = [];
            publishableProject = response.publishable;
            projectErrors = response.projectErrors;
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
        var coordinates = applicationModel.getUserGeoLocation();
        var data = {
          projectId: project.id,
          linkId: Math.abs(selectedProjectLink[0].linkId),
          coordinates: coordinates
        };
        backend.removeProjectLinkSplit(data, function (response) {
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

      var projectId = projectInfo.id;
      var coordinates = applicationModel.getUserGeoLocation();
      var dataJson = {
        linkIds: linkIds,
        linkStatus: statusCode,
        projectId: projectId,
        roadNumber: Number($('#roadAddressProjectForm').find('#tie')[0].value),
        roadPartNumber: Number($('#roadAddressProjectForm').find('#osa')[0].value),
        trackCode: Number($('#roadAddressProjectForm').find('#trackCodeDropdown')[0].value),
        discontinuity: Number($('#roadAddressProjectForm').find('#discontinuityDropdown')[0].value),
        roadEly: Number($('#roadAddressProjectForm').find('#ely')[0].value),
        roadLinkSource: Number(_.first(changedLinks).roadLinkSource),
        roadType: Number($('#roadAddressProjectForm').find('#roadTypeDropDown')[0].value),
        userDefinedEndAddressM: null,
        coordinates:coordinates
      };

      if(dataJson.trackCode === Track.Unknown.value){
        new ModalConfirm("Tarkista ajoratakoodi");
        applicationModel.removeSpinner();
        return false;
      }

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
              new ModalConfirm(successObject.errorMessage);
              applicationModel.removeSpinner();
            } else {
              publishableProject = successObject.publishable;
              projectErrors = successObject.projectErrors;
              eventbus.trigger('projectLink:projectLinksCreateSuccess');
              eventbus.trigger('roadAddress:projectLinksCreateSuccess');
              eventbus.trigger('roadAddress:projectLinksUpdated', successObject);
            }
          });
        }
        else {
          backend.updateProjectLinks(dataJson, function (successObject) {
            if (!successObject.success) {
              new ModalConfirm(successObject.errorMessage);
              applicationModel.removeSpinner();
            } else {
              publishableProject = successObject.publishable;
              projectErrors = successObject.projectErrors;
              eventbus.trigger('roadAddress:projectLinksUpdated', successObject);
            }
          });
        }
      } else {
        eventbus.trigger('roadAddress:projectLinksUpdateFailed', PRECONDITION_FAILED_412);
      }
      return true;
    };

    this.preSplitProjectLinks = function(suravage, nearestPoint){
      applicationModel.addSpinner();
      var form = $('#roadAddressProjectFormCut');
      var linkId = suravage.linkId;
      var projectId = projectInfo.id;
      var coordinates = applicationModel.getUserGeoLocation();
      var dataJson = {
        splitPoint: {
          x: nearestPoint.x,
          y: nearestPoint.y
        },
        statusA: LinkStatus.Transfer.value,
        statusB: LinkStatus.New.value,
        roadNumber: suravage.roadNumber,
        roadPartNumber: suravage.roadPartNumber,
        trackCode: suravage.trackCode,
        discontinuity: suravage.discontinuity,
        ely: suravage.elyCode,
        roadLinkSource: suravage.roadLinkSource,
        roadType: suravage.roadTypeId,
        projectId: projectId,
        coordinates:coordinates
      };
      backend.getPreSplitedData(dataJson, linkId, function(successObject){
        if (!successObject.success) {
          new ModalConfirm(successObject.errorMessage);
          applicationModel.removeSpinner();
        } else {
          eventbus.trigger('projectLink:preSplitSuccess', successObject.response);
        }
      }, function(failureObject){
        eventbus.trigger('roadAddress:projectLinksUpdateFailed', INTERNAL_SERVER_ERROR_500);
      });

    };

    this.getCutLine = function(linkId, splitPoint){
      applicationModel.addSpinner();
      var dataJson = {
        linkId: linkId,
        splitedPoint: {
          x: splitPoint.x,
          y: splitPoint.y
        }
      };
      backend.getCutLine(dataJson, function(successObject){
        if (!successObject.success) {
          new ModalConfirm(successObject.errorMessage);
          applicationModel.removeSpinner();
        } else {
          eventbus.trigger('split:splitedCutLine', successObject.response);
        }
      }, function(failureObject){
        eventbus.trigger('roadAddress:projectLinksUpdateFailed', BAD_REQUEST_400);
      });

    };

      this.saveCuttedProjectLinks = function(changedLinks, statusA, statusB){
      applicationModel.addSpinner();
      if (_.isUndefined(statusB)) {
        statusB = LinkStatus.New.description;
      }
      if (_.isUndefined(statusA)) {
        statusA = LinkStatus.Transfer.description;
      }
      var linkId = Math.abs(changedLinks[0].linkId);

      var projectId = projectInfo.id;
      var form = $('#roadAddressProjectFormCut');
      var coordinates = applicationModel.getUserGeoLocation();
      var objectA = _.find(LinkStatus, function(obj){
        return obj.description === statusA;
      });
      var objectB = _.find(LinkStatus, function(obj){
        return obj.description === statusB;
      });
      var dataJson = {
        splitPoint: {
          x: Number(form.find('#splitx')[0].value),
          y: Number(form.find('#splity')[0].value)
        },
        statusA: objectA.value,
        statusB: objectB.value,
        roadNumber: Number(form.find('#tie')[0].value),
        roadPartNumber: Number(form.find('#osa')[0].value),
        trackCode: Number(form.find('#trackCodeDropdown')[0].value),
        discontinuity: Number(form.find('#discontinuityDropdown')[0].value),
        ely: Number(form.find('#ely')[0].value),
        roadLinkSource: Number(_.first(changedLinks).roadLinkSource),
        roadType: Number(form.find('#roadTypeDropDown')[0].value),
        projectId: projectId,
        coordinates:coordinates
      };

      if(dataJson.trackCode === Track.Unknown.value){
        new ModalConfirm("Tarkista ajoratakoodi");
        applicationModel.removeSpinner();
        return false;
      }

      backend.saveProjectLinkSplit(dataJson, linkId, function(successObject){
        if (!successObject.success) {
          new ModalConfirm(successObject.reason);
          applicationModel.removeSpinner();
        } else{
          eventbus.trigger('projectLink:projectLinksSplitSuccess');
          eventbus.trigger('roadAddress:projectLinksUpdated', successObject);
          applicationModel.removeSpinner();
      }}, function(failureObject){
          new ModalConfirm(failureObject.reason);
          applicationModel.removeSpinner();
      });
      return true;
    };

    this.createProject = function (data, resolution) {
      var roadPartList = _.map(currentReservedParts.concat(newReservedParts), function (part) {
        return {
          roadNumber: part.roadNumber,
          roadPartNumber: part.roadPartNumber,
          ely: (part.newEly ? part.newEly: part.currentEly)
        };
      });

      var dataJson = {
        id: 0,
        status: 1,
        name: data[0].value,
        startDate: data[1].value,
        additionalInfo: data[2].value,
        roadPartList: roadPartList,
        resolution: resolution
      };

      backend.createRoadAddressProject(dataJson, function (result) {
        if (result.success) {
          projectInfo = {
            id: result.project.id,
            additionalInfo: result.project.additionalInfo,
            status: result.project.status,
            startDate: result.project.startDate,
            publishable: false
          };
          eventbus.trigger('roadAddress:projectSaved', result);
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result.errorMessage);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    this.deleteProject = function(projectId){
      backend.deleteRoadAddressProject(projectId, function (result) {
        if (result.success) {
          dirtyRoadPartList = [];
          currentProject = [];
        }
        else {
          eventbus.trigger('roadAddress:projectDeleteFailed', result.errorMessage);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    this.changeNewProjectLinkDirection = function (projectId, selectedLinks){
      applicationModel.addSpinner();
      var links = _.filter(selectedLinks, function(link) {return link.status !== LinkStatus.Terminated.value;});
      var coordinates = applicationModel.getUserGeoLocation();
      var dataJson = {
        projectId: projectId,
        roadNumber: selectedLinks[0].roadNumber,
        roadPartNumber: selectedLinks[0].roadPartNumber,
        links: links,
        coordinates: coordinates
      };
      backend.directionChangeNewRoadlink(dataJson, function(successObject) {
        if (!successObject.success) {
          eventbus.trigger('roadAddress:changeDirectionFailed', successObject.errorMessage);
          applicationModel.removeSpinner();
        } else {
          eventbus.trigger('changeProjectDirection:clicked');
        }
      });
    };

    this.changeNewProjectLinkCutDirection = function (projectId, selectedLinks){
      applicationModel.addSpinner();
      var links = _.filter(selectedLinks, function(link) {return link.status !== LinkStatus.Terminated.value;});
      var coordinates = applicationModel.getUserGeoLocation();
      var dataJson = {
        projectId: projectId,
        roadNumber: selectedLinks[0].roadNumber,
        roadPartNumber: selectedLinks[0].roadPartNumber,
        links: links,
        coordinates: coordinates
      };
      backend.directionChangeNewRoadlink(dataJson, function(successObject) {
        if (!successObject.success) {
          eventbus.trigger('roadAddress:changeDirectionFailed', successObject.errorMessage);
          applicationModel.removeSpinner();
        } else {
          eventbus.trigger('changeProjectDirection:clicked');
        }
      });
    };

    this.publishProject = function() {
      backend.sendProjectToTR(projectInfo.id, function(result) {
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

    var addSmallLabelWithIds = function(label, id){
      return '<label class="control-label-small" id='+ id+'>'+label+'</label>';
    };

    var updateFormInfo = function (formInfo) {
      $("#reservedRoads").append($("#reservedRoads").html(formInfo));
    };

    var parseRoadPartInfoToResultRow = function () {
      var listContent = '';
      var index = 0;
      _.each(self.getCurrentReservedParts(), function (row) {
          var button = deleteButton(index++, row.roadNumber, row.roadPartNumber);
          listContent += '<div class="form-reserved-roads-list">'+ button+
            addSmallLabelWithIds(row.roadNumber,'reservedRoadNumber') +
            addSmallLabelWithIds(row.roadPartNumber, 'reservedRoadPartNumber') +
            addSmallLabelWithIds((row.newLength ? row.newLength : row.currentLength), 'reservedRoadLength') +
            addSmallLabelWithIds((row.newDiscontinuity ? row.newDiscontinuity : row.currentDiscontinuity), 'reservedDiscontinuity') +
            addSmallLabelWithIds((row.newEly ? row.newEly : row.currentEly), 'reservedEly') +'</div>';
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


    var addToDirtyRoadPartList = function (queryResult) {
      var qRoadParts = [];
      _.each(queryResult.roadparts, function (row) {
        qRoadParts.push(row);
      });

      var sameElements = arrayIntersection(qRoadParts, currentReservedParts, function (arrayarow, arraybrow) {
        return arrayarow.roadNumber === arraybrow.roadNumber && arrayarow.roadPartNumber === arraybrow.roadPartNumber;
      });
      _.each(sameElements, function (row) {
        _.remove(qRoadParts, row);
      });
      _.each(qRoadParts, function (row) {
        currentReservedParts.push(row);
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

    this.getCurrentReservedParts = function() {
      return currentReservedParts;
    };

    this.getNewReservedParts = function () {
      return newReservedParts;
    };

    this.setReservedParts = function (list) {
      var reservedAndNew = _.groupBy(list, function (part) {
        return (_.isUndefined(part.currentLength) && _.isUndefined(part.currentEly) && _.isUndefined(part.currentDiscontinuity));
      });
      if (reservedAndNew.true) {
        newReservedParts = reservedAndNew.true;
      } else newReservedParts = [];
      if (reservedAndNew.false) {
        currentReservedParts = reservedAndNew.false;
      } else currentReservedParts = [];
    };

    this.getAllReservedParts = function () {
      return self.getCurrentReservedParts().concat(self.getNewReservedParts());
    };

    this.setProjectErrors = function(errors) {
      projectErrors = errors;
    };

    this.getProjectErrors = function(){
      return projectErrors;
    };

    this.pushCoordinates = function(button) {
      coordinateButtons.push(button);
    };

    this.clearCoordinates = function(button) {
      coordinateButtons = [];
    };

    this.setTmpDirty = function(editRoadLinks){
      dirtyProjectLinks = editRoadLinks;
    };

    this.getTmpDirty = function(){
      return dirtyProjectLinks;
    };

    this.isDirty = function() {
      return dirtyProjectLinks.length > 0;
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

    eventbus.on('projectCollection:clickCoordinates',function (event, map) {
      var currentCoordinates =  map.getView().getCenter();
      var errorIndex = event.currentTarget.id;
      var errorCoordinates = _.find(coordinateButtons, function (b) {
        return b.index == errorIndex;
      }).coordinates;
      var index = _.findIndex(errorCoordinates, function (coordinates) {
        return coordinates.x == currentCoordinates[0] && coordinates.y == currentCoordinates[1];
      });
      if (index >= 0 && index + 1 < errorCoordinates.length) {
        map.getView().setCenter([errorCoordinates[index + 1].x, errorCoordinates[index + 1].y]);
        map.getView().setZoom(errorCoordinates[index + 1].zoom);
      } else {
        map.getView().setCenter([errorCoordinates[0].x, errorCoordinates[0].y]);
        map.getView().setZoom(errorCoordinates[0].zoom);
      }
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
