(function(root) {
  root.RoadAddressProjectCollection = function(backend) {
    var roadAddressProjects = [];
    var currentRoadSegmentList = [];
    var dirtyRoadSegmentLst = [];
    var projectinfo;
    var fetchedProjectLinks = [];

    var projectLinks = function() {
        return _.flatten(fetchedProjectLinks);
    };

    this.getAll = function () {
      return _.map(projectLinks(), function(projectLink) {
        return projectLink.getData();
      });
    };

    this.fetch = function(boundingBox, zoom, projectId) {
      backend.getProjectLinks({boundingBox: boundingBox, zoom: zoom, projectId: projectId}, function(fetchedLinks) {
          fetchedProjectLinks = _.map(fetchedLinks, function(projectLinkGroup) {
              return _.map(projectLinkGroup, function(projectLink) {
                  return new ProjectLinkModel(projectLink);
              });
          });
      });
    };

    this.getProjects = function () {
      return backend.getRoadAddressProjects(function (projects) {
        roadAddressProjects = projects;
      });
    };

    this.getProjectsWithLinksById = function (projectId) {
      return backend.getProjectsWithLinksById(projectId, function (projects) {
        roadAddressProjects = projects.project;
        roadAddressProjectLinks = projects.projectLinks;
      });
    };

    this.clearRoadAddressProjects = function () {
      roadAddressProjects = [];
      dirtyRoadSegmentLst = [];
      currentRoadSegmentList = [];
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
        roadPartList: dirtyRoadSegmentLst
      };

      backend.saveRoadAddressProject(dataJson, function (result) {
        if (result.success === "ok") {
          projectinfo = {
            id: result.project.id,
            additionalInfo: result.project.additionalInfo,
            status: result.project.status,
            startDate: result.project.startDate
          };
          eventbus.trigger('roadAddress:projectSaved', result);
          dirtyRoadSegmentLst = [];
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    this.createProject = function (data, currentProject) {

      var dataJson = {
        id: 0,
        status: 1,
        name: data[0].value,
        startDate: data[1].value,
        additionalInfo: data[2].value,
        roadPartList: dirtyRoadSegmentLst
      };

      backend.createRoadAddressProject(dataJson, function (result) {
        if (result.success === "ok") {
          projectinfo = {
            id: result.project.id,
            additionalInfo: result.project.additionalInfo,
            status: result.project.status,
            startDate: result.project.startDate
          };
          eventbus.trigger('roadAddress:projectSaved', result);
          dirtyRoadSegmentLst = [];
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result);
        }
      }, function () {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    var addSmallLabel = function (label) {
      return '<label class="control-label-small">' + label + '</label>';
    };

    var updateFormInfo = function (formInfo) {
      $("#roadpartList").append($("#roadpartList").html(formInfo));
    };

    var parseroadpartinfoToresultRow = function () {
      var listContent = '';
      _.each(currentRoadSegmentList, function (row) {
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

      var sameElements = arrayIntersection(qRoadparts, currentRoadSegmentList, function (arrayarow, arraybrow) {
        return arrayarow.roadPartId === arraybrow.roadPartId;
      });
      _.each(sameElements, function (row) {
        _.remove(qRoadparts, row);
      });
      _.each(qRoadparts, function (row) {
        currentRoadSegmentList.push(row);
        dirtyRoadSegmentLst.push(row);
      });
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
