(function(root) {
  root.RoadAddressProjectCollection = function(backend) {
    var roadAddressProjects = [];
    var roadAddressProjects2 = [{name: 'proj1', state: 1}, {name: 'projeto2', state: 1}];

    this.getAll = function(){
      return backend.getRoadAddressProjects(function(projects){
        roadAddressProjects = projects;
      });
    };

    this.clearRoadAddressProjects = function(){
      roadAddressProjects = [];
    };

    this.createProject = function(data, currentProject){
      var dataJson = {id: 0, status:1 ,name : data[0].value, startDate: data[1].value , additionalInfo :  data[2].value, roadNumber : data[3].value === '' ? 0 : parseInt(data[3].value), startPart: data[4].value === '' ? 0 : parseInt(data[4].value), endPart : data[5].value === '' ? 0 : parseInt(data[5].value) };
      backend.createRoadAddressProject(dataJson, function(result) {
        console.log(result.success);
        if(result.success === "ok") {
          eventbus.trigger('roadAddress:projectSaved', result);
        }
        else {
          eventbus.trigger('roadAddress:projectValidationFailed', result);
        }
      }, function() {
        eventbus.trigger('roadAddress:projectFailed');
      });
    };

    this.checkIfReserved = function(data) {
        return backend.checkIfRoadpartReserved(data[3].value === '' ? 0 : parseInt(data[3].value), data[4].value === '' ? 0 : parseInt(data[4].value), data[5].value === '' ? 0 : parseInt(data[5].value))
            .then(function (validationResult) {
               if (validationResult.success!=="ok"){
                   eventbus.trigger('roadAddress:projectValidationFailed', validationResult);
               }
            });
    };
  };
})(this);
