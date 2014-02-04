package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.user.Configuration

object LayersJson {
  def layers(userConfig: Configuration): String = {
   val municipalityNumber = userConfig.municipalityNumber.getOrElse(235)

    s"""{
    "layers": [
        {
            "wmsName":"bussit",
            "type":"busstoplayer",
            "id":236,
            "minScale":5000,
            "wmsUrl":"/data/dummy/busstops.json",
            "url":"/api/assets?assetTypeId=10&municipalityNumber=${municipalityNumber}&validityPeriod=future",
            "roadLinesUrl" :"/api/roadlinks?municipalityNumber=${municipalityNumber}&validityPeriod=future",
            "maxScale":1 ,
            "orgName":"LiVi",
            "inspire":"Ominaisuustiedot",
            "name" : "Tulevat",
            "opacity" : 30
        },{
            "wmsName":"bussit",
            "type":"busstoplayer",
            "id":237,
            "minScale":5000,
            "wmsUrl":"/data/dummy/busstops.json",
            "url":"/api/assets?assetTypeId=10&municipalityNumber=${municipalityNumber}&validityPeriod=past",
            "roadLinesUrl" :"/api/roadlinks?municipalityNumber=${municipalityNumber}&validityPeriod=past",
            "maxScale":1 ,
            "orgName":"LiVi",
            "inspire":"Ominaisuustiedot",
            "name" : "Käytöstä poistuneet",
            "opacity" : 30
        }

    ]
}"""
  }
}