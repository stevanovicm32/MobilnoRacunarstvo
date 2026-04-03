package main

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/stevanovicm32/MobilnoRacunarstvo/internal/repository"
)

func main() {
	
	repository.InitDB()

	// TODO: Move to API
	r := gin.Default()

	r.GET("/health", func(ctx *gin.Context) {
		ctx.JSON(http.StatusOK, gin.H{
			"mesage": "server healthy",
		})
	})

	r.Run()
}
