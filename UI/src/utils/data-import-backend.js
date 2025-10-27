(function (root) {
  root.CsvBackend = function() {
    var self = this;

    this.getMunicipalities = function(success, failure) {
      $.ajax({
        url: "api/municipalities/byUser",
        type: "get",
        success: success,
        error: failure
      });
    };

      this.uploadFile = function(formData, assetType, success, failure) {
          $.ajax({
              url: 'api/import/' + assetType,
              type: 'POST',
              data: formData,
              processData: false,
              contentType: false,
              success: function(response) {
                  if (typeof success === 'function') {
                      success(response);
                  }
              },
              error: function(jqXHR, textStatus, errorThrown) {
                  console.error('Upload failed:', textStatus, errorThrown);
                  if (typeof failure === 'function') {
                      failure(jqXHR, textStatus, errorThrown);
                  }
              }
          });
      };

    this.getJobs = function() {
      return $.getJSON('api/import/log');
    };

    this.getJob = function(id) {
      return $.getJSON('api/import/log/' + id);
    };

    this.getJobsByIds = function(ids) {
      return $.getJSON('api/import/logs/' + ids);
    };

      this.getComplementaryRoadLinksByMunicipality = function(municipalityIds, success, failure) {
          var query = municipalityIds.join(',');
          var url = 'api/roadlinks/complementaryIds?municipalities=' + encodeURIComponent(query);

          $.ajax({
              url: url,
              type: 'GET',
              dataType: 'json',
              success: function(data) {
                  if (success) success(data);
              },
              error: function(xhr, status, error) {
                  if (failure) failure(error);
              }
          });
      };

      this.deleteRoadLinks = function(linkIdsToDelete, success, failure) {
          if (!Array.isArray(linkIdsToDelete) || linkIdsToDelete.length === 0) {
              console.error("deleteRoadLinks called with invalid linkIds", linkIdsToDelete);
              if (typeof failure === 'function') {
                  failure({ status: 400 }, "error", "No linkIds provided");
              }
              return;
          }

          var query = linkIdsToDelete.join(',');

          $.ajax({
              url: 'api/roadlinks/complementaryLinksToDelete?linkIds=' + encodeURIComponent(query),
              type: 'DELETE',
              dataType: 'text',
              success: function(data, status, xhr) {
                  if (typeof success === 'function') success(data, status, xhr);
              },
              error: function(xhr, status, err) {
                  console.error("AJAX error:", status, err);
                  if (typeof failure === 'function') failure(xhr, status, err);
              }
          });
      };
  };
}(this));
