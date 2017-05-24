(function(root) {
  root.RoadAddressProjectCollection = function(backend) {
    var roadAddressProjects = [];
    var currentRoadPartList = [];
    var dirtyRoadPartList = [];
    var projectinfo;
    var fetchedProjectLinks = [];
    var roadAddressProjectLinks = [];
    var projectLinksSaved = [];
    var dirtyProjectLinks = [];
    var self = this;
    var STATUS_NOT_HANDLED = 0;
    var STATUS_TERMINATED = 1;
    var BAD_REQUEST_400 = 400;
    var UNAUTHORIZED_401 = 401;
    var PRECONDITION_FAILED_412 = 412;
    var INTERNAL_SERVER_ERROR_500 = 500;

    var projectLinks = function() {
      return _.flatten(fetchedProjectLinks);
    };

    this.getAll = function () {
      return _.map(projectLinks(), function(projectLink) {
        return projectLink.getData();
      });
    };

    this.getSavedLinks = function(){
      return projectLinksSaved;
    };

    this.getMultiSelectIds = function (linkId) {
      var chain = _.find(fetchedProjectLinks, function (linkChain) {
        var pureChain = _.map(linkChain, function(l) { return l.getData(); });
        return _.some(pureChain, {"linkId": linkId});
      });
      return _.map(chain, function (link) { return link.getData().linkId; });
    };

    this.getByLinkId = function (ids) {
      var ProjectLinks = _.filter(_.flatten(fetchedProjectLinks), function (projectLink){
        return _.contains(ids, projectLink.getData().linkId);
      });
      return ProjectLinks;
    };

    this.fetch = function(boundingBox, zoom, projectId) {
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
          eventbus.trigger('roadAddressProject:fetched', self.getAll());
        });
    };

    this.getProjects = function () {
      return backend.getRoadAddressProjects(function (projects) {
        roadAddressProjects = projects;
      });
    };

    this.getProjectsWithLinksById = function (projectId) {
      return backend.getProjectsWithLinksById(projectId, function (result) {
        roadAddressProjects = result.projects;
        roadAddressProjectLinks = result.projectLinks;
        projectinfo = {
          id: result.projects.id,
          publishable: false
        };
        eventbus.trigger('roadAddressProject:projectFetched', projectinfo.id);
      });
    };

    this.clearRoadAddressProjects = function () {
      roadAddressProjects = [];
      dirtyRoadPartList = [];
      currentRoadPartList = [];
      dirtyProjectLinks = [];
      projectinfo=undefined;
      backend.abortLoadingProject();
    };

    this.saveProject = function (data, currentProject) {
      var projectid = 0;
      if (projectinfo !== undefined) {
        projectid = projectinfo.id;
      } else if (currentProject!==undefined && currentProject.id!==undefined)
      {
        projectid=currentProject.id;
      }
      var dataJson = {
        id: projectid,
        status: 1,
        name: data[0].value,
        startDate: data[1].value,
        additionalInfo: data[2].value,
        roadPartList: dirtyRoadPartList
      };

      backend.saveRoadAddressProject(dataJson, function (result) {
        if (result.success === "ok") {
          projectinfo = {
            id: result.project.id,
            additionalInfo: result.project.additionalInfo,
            status: result.project.status,
            startDate: result.project.startDate,
            publishable: false
          };
          eventbus.trigger('roadAddress:projectSaved', result);
          dirtyRoadPartList = [];
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };


    this.saveProjectLinks = function(selectedProjectLink, currentProject) {
      applicationModel.addSpinner();
      var linkIds = _.map(selectedProjectLink,function (t){
        if(!_.isUndefined(t.linkId)){
          return t.linkId;
        } else return t;
      });

      projectLinksSaved = projectLinksSaved.concat(linkIds);

      var projectId = currentProject.id;

      var data = {'linkIds': linkIds, 'projectId': projectId, 'newStatus': STATUS_TERMINATED};

      if(!_.isEmpty(linkIds) && typeof projectId !== 'undefined' && projectId !== 0){
        backend.updateProjectLinks(data, function(errorObject) {
          if (errorObject.status == INTERNAL_SERVER_ERROR_500 || errorObject.status == BAD_REQUEST_400) {
            eventbus.trigger('roadAddress:projectLinksUpdateFailed', errorObject.status);
          }
        });
      } else {
        eventbus.trigger('roadAddress:projectLinksUpdateFailed', PRECONDITION_FAILED_412);
      }
    };

    this.createProject = function (data, currentProject) {

      var dataJson = {
        id: 0,
        status: 1,
        name: data[0].value,
        startDate: data[1].value,
        additionalInfo: data[2].value,
        roadPartList: dirtyRoadPartList
      };

      backend.createRoadAddressProject(dataJson, function (result) {
        if (result.success === "ok") {
          projectinfo = {
            id: result.project.id,
            additionalInfo: result.project.additionalInfo,
            status: result.project.status,
            startDate: result.project.startDate,
            publishable: false
          };
          eventbus.trigger('roadAddress:projectSaved', result);
          dirtyRoadPartList = [];
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    this.publishProject = function() {
      // TODO: Project publish action
      console.log("TODO");
    };

    var addSmallLabel = function (label) {
      return '<label class="control-label-small">' + label + '</label>';
    };

    var updateFormInfo = function (formInfo) {
      $("#roadpartList").append($("#roadpartList").html(formInfo));
    };

    var parseroadpartinfoToresultRow = function () {
      var listContent = '';
      _.each(currentRoadPartList, function (row) {
          listContent += addSmallLabel(row.roadNumber) + addSmallLabel(row.roadPartNumber) + addSmallLabel(row.length) + addSmallLabel(row.discontinuity) + addSmallLabel(row.ely) +
            '</div>';
        }
      );
      return listContent;
    };


    var addToCurrentRoadPartList = function (queryresult) {
      var qRoadparts = [];
      _.each(queryresult.roadparts, function (row) {
        qRoadparts.push(row);
      });

      var sameElements = arrayIntersection(qRoadparts, currentRoadPartList, function (arrayarow, arraybrow) {
        return arrayarow.roadPartId === arraybrow.roadPartId;
      });
      _.each(sameElements, function (row) {
        _.remove(qRoadparts, row);
      });
      _.each(qRoadparts, function (row) {
        currentRoadPartList.push(row);
        dirtyRoadPartList.push(row);
      });
    };

    this.setDirty = function(editedRoadLinks) {
      dirtyProjectLinks = editedRoadLinks;
      eventbus.trigger('roadAddress:projectLinksEdited');
    };

    this.getDirty = function() {
      return dirtyProjectLinks;
    };


    function arrayIntersection(a, b, areEqualFunction) {
      return _.filter(a, function(aElem) {
        return _.any(b, function(bElem) {
          return areEqualFunction(aElem,bElem);
        });
      });
    }


    eventbus.on('clearproject', function() {
      this.clearRoadAddressProjects();
    });


    this.checkIfReserved = function (data) {
      return backend.checkIfRoadpartReserved(data[3].value === '' ? 0 : parseInt(data[3].value), data[4].value === '' ? 0 : parseInt(data[4].value), data[5].value === '' ? 0 : parseInt(data[5].value))
        .then(function (validationResult) {
          if (validationResult.success !== "ok") {
            eventbus.trigger('roadAddress:projectValidationFailed', validationResult);
          } else {
            addToCurrentRoadPartList(validationResult);
            updateFormInfo(parseroadpartinfoToresultRow());
            eventbus.trigger('roadAddress:projectValidationSucceed');
          }
        });
    };

    var ProjectLinkModel = function(data) {

      var getData = function() {
        return data;
      };

      return {
        getData: getData
      };
    };
  };
})(this);
