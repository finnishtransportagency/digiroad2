package fi.liikennevirasto.digiroad2

import org.scalatra.{Ok, ScalatraServlet}

class TierekisteriTestApi extends ScalatraServlet {
  get("/pysakit") {
    Ok("OK")
  }

  get("/pysakit/:liviId"){
    Ok("OK")
  }

  put("/pysakit/:liviId"){
    Ok("OK")
  }

  post("/pysakit"){
    Ok("OK")
  }

  delete("/pysakit/:liviId"){
    Ok("OK")
  }
}

