(function(root) {
  root.RoadAddressProjectCollection = function(backend) {
    var roadAddressProjects = [];
    var roadAddressProjects2 = [{name: 'proj1', state: 1}, {name: 'projeto2', state: 1}];
    var currentRoadSegmentList= [];
    var dirtyRoadSegmentLst=[];
    var projectinfo;
    this.getAll = function(){
      return backend.getRoadAddressProjects(function(projects){
        roadAddressProjects = projects;
      });
    };


    this.clearRoadAddressProjects = function(){
      roadAddressProjects = [];
      dirtyRoadSegmentLst=[];
      currentRoadSegmentList= [];
    };

    this.createProject = function(data, currentProject){
      var projectid=0;
      if (projectinfo!=null)
      {
        projectid=projectinfo.id;
      }
      var dataJson = {id: projectid, status:1 ,name : data[0].value, startDate: data[1].value , additionalInfo :  data[2].value,roadpartlist: dirtyRoadSegmentLst};

      backend.createRoadAddressProject(dataJson, function(result) {
        console.log(result.success);
        if(result.success === "ok") {
          projectinfo={id:result.project.id, additionalInfo:result.project.additionalInfo, status:result.project.status, startDate:result.project.startDate};
          eventbus.trigger('roadAddress:projectSaved', result);
        dirtyRoadSegmentLst=[];
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result);
        }
      }, function() {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

      var addSmallLabel = function(label){
          return '<label class="control-label-small">'+label+'</label>';
      };

      var updateforminfo = function(formInfo)
      {
          $("#roadpartList").html(formInfo)
      };

      var parseroadpartinfoToresultRow = function() {
      var listContent='';
      _.each(currentRoadSegmentList, function(row) {
        listContent+= addSmallLabel(row.roadNumber)+ addSmallLabel(row.roadPart)+ addSmallLabel(row.lenght)+ addSmallLabel(row.discontinuity)+ addSmallLabel(row.ely) +
            '</div>';
          }

      );
        return listContent
      };


      var addToCurrentRoadPartList = function(querryresult) {
          var qRoadparts=[];
          _.each(querryresult.roadparts, function(row) {
              qRoadparts.push(row)
          });

          var sameElements=arrayIntersection(qRoadparts,currentRoadSegmentList,function(arrayarow,arraybrow){return arrayarow.roadpartid===arraybrow.roadpartid; });
          _.each(sameElements, function(row)
          {
              _.remove(qRoadparts,row)
          });
          _.each(qRoadparts,function (row){
              currentRoadSegmentList.push(row);
              dirtyRoadSegmentLst.push(row);
          });
      };


      function arrayIntersection(a, b, areEqualFunction) {
          var results = [];

          for(var i = 0; i < a.length; i++) {
              var aElement = a[i];
              var existsInB = _.any(b, function(bElement) { return areEqualFunction(bElement, aElement); });

              if(existsInB) {
                  results.push(aElement);
              }
          }
          return results;
      }


      this.checkIfReserved = function(data) {
        return backend.checkIfRoadpartReserved(data[3].value === '' ? 0 : parseInt(data[3].value), data[4].value === '' ? 0 : parseInt(data[4].value), data[5].value === '' ? 0 : parseInt(data[5].value))
            .then(function (validationResult) {
               if (validationResult.success!=="ok"){
                   eventbus.trigger('roadAddress:projectValidationFailed', validationResult);
               } else
               {
                   addToCurrentRoadPartList(validationResult);
                   updateforminfo(parseroadpartinfoToresultRow());
               }
            });
    };
  };
})(this);
